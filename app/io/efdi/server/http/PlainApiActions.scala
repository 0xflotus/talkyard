/**
 * Copyright (c) 2012-2015 Kaj Magnus Lindberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.efdi.server.http

import com.debiki.core._
import com.debiki.core.Prelude._
import controllers.LoginController
import controllers.Utils
import debiki._
import debiki.DebikiHttp._
import debiki.RateLimits.NoRateLimits
import java.{util => ju}
import play.api._
import play.{api => p}
import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


/** Play Framework Actions for requests to Debiki's HTTP API.
  */
private[http] object PlainApiActions {


  def PlainApiAction(rateLimits: RateLimits, allowAnyone: Boolean = false) =
    PlainApiActionImpl(rateLimits, adminOnly = false, staffOnly = false, allowAnyone)

  def PlainApiActionStaffOnly =
    PlainApiActionImpl(NoRateLimits, adminOnly = false, staffOnly = true)

  val PlainApiActionAdminOnly =
    PlainApiActionImpl(NoRateLimits, adminOnly = true, staffOnly = false)




  /** Checks the session id and xsrf token and looks up the user rate limits the endpoint.
    *
    * Throws Forbidden if this is a POST request with no valid xsrf token.
    * Creates a new xsrf token cookie, if there is none, or if it's invalid.
    *
    * Throws Forbidden, and deletes the session id cookie, if any login id
    * doesn't map to any login entry.
    * The SidStatusRequest.sidStatus passed to the action is either SidAbsent or a SidOk.
    */
  def PlainApiActionImpl(rateLimits: RateLimits, adminOnly: Boolean,
        staffOnly: Boolean, allowAnyone: Boolean = false) =
      new ActionBuilder[ApiRequest] {

    override def composeAction[A](action: Action[A]) = {
      ExceptionAction.async(action.parser) { request: Request[A] =>
        action(request)
      }
    }

    override def invokeBlock[A](request: Request[A], block: ApiRequest[A] => Future[Result]) = {

      val site = DebikiHttp.lookupSiteOrThrow(request, Globals.systemDao)

      val (actualSidStatus, xsrfOk, newCookies) =
        DebikiSecurity.checkSidAndXsrfToken(request, site.id, maySetCookies = true)

      // Ignore and delete any broken session id cookie.
      val (mendedSidStatus, deleteSidCookie) =
        if (actualSidStatus.isOk) (actualSidStatus, false)
        else (SidAbsent, true)

      val (anyBrowserId, moreNewCookies) =
        BrowserId.checkBrowserId(request, maySetCookies = true)

      // Parts of `block` might be executed asynchronously. However any LoginNotFoundException
      // should happen before the async parts, because access control should be done
      // before any async computations are started. So I don't try to recover
      // any AsyncResult(future-result-that-might-be-a-failure) here.
      val resultOldCookies: Future[Result] =
        try {
          runBlockIfAuthOk(request, site, mendedSidStatus, xsrfOk, anyBrowserId, block)
        }
        catch {
          case e: Utils.LoginNotFoundException =>
            // This might happen if I manually deleted stuff from the
            // database during development, or if the server has fallbacked
            // to a standby database.
            throw ResultException(InternalErrorResult(
              "DwE034ZQ3", o"""Internal error, please try again, sorry. For example,
               reload the page.\n(A certain login id has become invalid. I just gave you
               a new id, and you will probably need to login again.)""")
              .discardingCookies(DiscardingSecureCookie(Sid.CookieName)))
        }

      val resultOkSid =
        if (newCookies.isEmpty && moreNewCookies.isEmpty && !deleteSidCookie) {
          resultOldCookies
        }
        else {
          resultOldCookies map { result =>
            var resultWithCookies = result
              .withCookies((newCookies ::: moreNewCookies): _*)
              .withHeaders(SafeActions.MakeInternetExplorerSaveIframeCookiesHeader)
            if (deleteSidCookie) {
              resultWithCookies =
                resultWithCookies.discardingCookies(DiscardingSecureCookie(Sid.CookieName))
            }
            resultWithCookies
          }
        }

      resultOkSid
    }


    def runBlockIfAuthOk[A](request: Request[A], site: SiteIdHostname, sidStatus: SidStatus,
          xsrfOk: XsrfOk, browserId: Option[BrowserId], block: ApiRequest[A] => Future[Result]) = {
      val dao = Globals.siteDao(site.id)
      dao.perhapsBlockGuest(request, sidStatus, browserId)

      var anyUser = Utils.loadUserOrThrow(sidStatus, dao)
      var logoutBecauseSuspended = false
      if (anyUser.exists(_.isSuspendedAt(new ju.Date))) {
        anyUser = None
        logoutBecauseSuspended = true
      }

      if (staffOnly && !anyUser.exists(_.isStaff))
        throwForbidden("DwE7BSW3", "Please login as admin or moderator")

      if (adminOnly && !anyUser.exists(_.isAdmin))
        throwForbidden("DwE1GfK7", "Please login as admin")

      if (!allowAnyone) {
        val siteSettings = dao.loadWholeSiteSettings()
        if (!anyUser.exists(_.isApprovedOrStaff) && siteSettings.userMustBeApproved.asBoolean)
          throwForbidden("DwE4HKG5", "Not approved")
        if (!anyUser.exists(_.isAuthenticated) && siteSettings.userMustBeAuthenticated.asBoolean)
          throwForbidden("DwE6JGY2", "Not authenticated")
      }

      val apiRequest = ApiRequest[A](
        site, sidStatus, xsrfOk, browserId, anyUser, dao, request)

      RateLimiter.rateLimit(rateLimits, apiRequest)

      // COULD use markers instead for site id and ip, and perhaps uri too? Dupl code [5KWC28]
      val requestUriAndIp = s"site $site, ip ${apiRequest.ip}: ${apiRequest.uri}"
      p.Logger.debug(s"API request started [DwM6L8], " + requestUriAndIp)

      val timer = Globals.metricRegistry.timer(request.path)
      val timerContext = timer.time()
      var result = try {
        block(apiRequest)
      }
      catch {
        case ex: ResultException =>
          // This is fine, probably just a 403 Forbidden exception or 404 Not Found, whatever.
          p.Logger.debug(
            s"API request result exception [EsE4K2J2]: $ex, $requestUriAndIp")
          throw ex
        case ex: Exception =>
          p.Logger.warn(s"API request unexpected exception [EsE4JYU0], $requestUriAndIp", ex)
          throw ex
      }
      finally {
        timerContext.stop()
      }

      result onComplete {
        case Success(r) =>
          p.Logger.debug(
            s"API request ended, status ${r.header.status} [DwM9Z2], $requestUriAndIp")
        case Failure(exception) =>
          p.Logger.debug(
            s"API request exception: ${classNameOf(exception)} [DwE4P7], $requestUriAndIp")
      }

      if (logoutBecauseSuspended) {
        // We won't get here if e.g. a 403 Forbidden exception was thrown because 'user' was
        // set to None. How solve that?
        result = result.map(_.discardingCookies(LoginController.DiscardingSessionCookie))
      }
      result
    }
  }
}
