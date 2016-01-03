/**
 * Copyright (c) 2013-2015 Kaj Magnus Lindberg
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

package io.efdi.server

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki._
import debiki.DebikiHttp._
import debiki.RateLimits.NoRateLimits
import java.{util => ju}
import debiki.dao.SiteDao
import play.api._
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.JsValue
import play.{api => p}
import play.api.mvc._
import play.api.Play.current
import scala.concurrent.Future
import scala.util.Try


package object http {

  import io.efdi.server.http.PlainApiActions._


  implicit class RichString2(value: String) {
    def toIntOrThrow(errorCode: String, errorMessage: String) =
      value.toIntOption getOrElse throwBadRequest(errorCode, errorMessage)

    def toLongOrThrow(errorCode: String, errorMessage: String) =
      Try(value.toLong).toOption getOrElse throwBadRequest(errorCode, errorMessage)
  }


  case class ApiRequest[A](
    siteIdAndCanonicalHostname: SiteIdHostname,
    sid: SidStatus,
    xsrfToken: XsrfOk,
    browserId: Option[BrowserId],
    user: Option[User],
    dao: SiteDao,
    request: Request[A]) extends DebikiRequest[A] {
  }

  /** A request with no post data. */
  type GetRequest = ApiRequest[Unit]

  type PageGetRequest = PageRequest[Unit]

  /** A request with form data.
    * @deprecated Use ApiRequest[JsonOrFormDataBody] instead — no, use JsonPostRequest.
    */
  type FormDataPostRequest = ApiRequest[Map[String, Seq[String]]]

  type JsonPostRequest = ApiRequest[JsValue]


  val ExceptionAction = SafeActions.ExceptionAction


  def AsyncGetAction(f: GetRequest => Future[Result]): mvc.Action[Unit] =
    PlainApiAction(NoRateLimits).async(BodyParsers.parse.empty)(f)

  def AsyncGetActionRateLimited(rateLimits: RateLimits)(f: GetRequest => Future[Result])
        : mvc.Action[Unit] =
    PlainApiAction(rateLimits).async(BodyParsers.parse.empty)(f)

  def GetAction(f: GetRequest => Result) =
    PlainApiAction(NoRateLimits)(BodyParsers.parse.empty)(f)

  def GetActionAllowAnyone(f: GetRequest => Result) =
    PlainApiAction(NoRateLimits, allowAnyone = true)(BodyParsers.parse.empty)(f)

  def GetActionRateLimited(rateLimits: RateLimits, allowAnyone: Boolean = false)(
        f: GetRequest => Result) =
    PlainApiAction(rateLimits, allowAnyone = allowAnyone)(BodyParsers.parse.empty)(f)

  def StaffGetAction(f: GetRequest => Result) =
    PlainApiActionStaffOnly(BodyParsers.parse.empty)(f)

  def AdminGetAction(f: GetRequest => Result) =
    PlainApiActionAdminOnly(BodyParsers.parse.empty)(f)


  def JsonOrFormDataPostAction
        (rateLimits: RateLimits, maxBytes: Int, allowAnyone: Boolean = false)
        (f: ApiRequest[JsonOrFormDataBody] => Result) =
    PlainApiAction(rateLimits, allowAnyone = allowAnyone)(
      JsonOrFormDataBody.parser(maxBytes = maxBytes))(f)

  def AsyncJsonOrFormDataPostAction
        (rateLimits: RateLimits, maxBytes: Int, allowAnyone: Boolean = false)
        (f: ApiRequest[JsonOrFormDataBody] => Future[Result]): mvc.Action[JsonOrFormDataBody] =
    PlainApiAction(rateLimits, allowAnyone = allowAnyone).async(
      JsonOrFormDataBody.parser(maxBytes = maxBytes))(f)


  def AsyncPostJsonAction(rateLimits: RateLimits, maxLength: Int, allowAnyone: Boolean = false)(
        f: JsonPostRequest => Future[Result]) =
  PlainApiAction(rateLimits, allowAnyone = allowAnyone).async(
    BodyParsers.parse.json(maxLength = maxLength))(f)

  def PostJsonAction(rateLimits: RateLimits, maxLength: Int, allowAnyone: Boolean = false)(
        f: JsonPostRequest => Result) =
    PlainApiAction(rateLimits, allowAnyone = allowAnyone)(
      BodyParsers.parse.json(maxLength = maxLength))(f)

  def StaffPostJsonAction(maxLength: Int)(f: JsonPostRequest => Result) =
    PlainApiActionStaffOnly(
      BodyParsers.parse.json(maxLength = maxLength))(f)

  def AdminPostJsonAction(maxLength: Int)(f: JsonPostRequest => Result) =
    PlainApiActionAdminOnly(
      BodyParsers.parse.json(maxLength = maxLength))(f)


  def PostFilesAction(rateLimits: RateLimits, maxLength: Int, allowAnyone: Boolean = false)(
        f: ApiRequest[Either[p.mvc.MaxSizeExceeded, MultipartFormData[TemporaryFile]]] => Result) =
    PlainApiAction(rateLimits, allowAnyone = allowAnyone)(
        BodyParsers.parse.maxLength(maxLength, BodyParsers.parse.multipartFormData))(f)



  /** The real ip address of the client, unless a fakeIp url param or dwCoFakeIp cookie specified
    * In prod mode, an e2e test password cookie is required.
    *
    * (If 'fakeIp' is specified, actions.SafeActions.scala copies the value to
    * the dwCoFakeIp cookie.)
    */
  def realOrFakeIpOf(request: play.api.mvc.Request[_]): String = {
    val fakeIp = request.queryString.get("fakeIp").flatMap(_.headOption).orElse(
      request.cookies.get("dwCoFakeIp").map(_.value))  getOrElse {
      return request.remoteAddress
    }

    if (Play.isProd) {
      val password = getE2eTestPassword(request) getOrElse {
        throwForbidden(
          "DwE6KJf2", "Fake ip specified, but no e2e test password cookie — required in prod mode")
      }
      val correctPassword = debiki.Globals.e2eTestPassword getOrElse {
        throwForbidden(
          "DwE7KUF2", "Fake ips not allowed, because no e2e test password has been configured")
      }
      if (password != correctPassword) {
        throwForbidden(
          "DwE2YUF2", "Fake ip forbidden: Wrong e2e test password")
      }
    }

    // Dev or test mode, or correct password, so:
    fakeIp
  }


  def getE2eTestPassword(request: play.api.mvc.Request[_]): Option[String] =
    request.queryString.get("e2eTestPassword").flatMap(_.headOption).orElse(
      request.cookies.get("dwCoE2eTestPassword").map(_.value)).orElse( // dwXxx obsolete. esXxx now
      request.cookies.get("esCoE2eTestPassword").map(_.value))


  def hasOkE2eTestPassword(request: play.api.mvc.Request[_]): Boolean = {
    getE2eTestPassword(request) match {
      case None => false
      case Some(password) =>
        val correctPassword = debiki.Globals.e2eTestPassword getOrElse throwForbidden(
          "EsE5GUM2", "There's an e2e test password in the request, but not in any config file")
        if (password != correctPassword) {
          throwForbidden("EsE2FWK4", "The e2e test password in the request is wrong")
        }
        true
    }
  }

}
