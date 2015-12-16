/**
 * Copyright (C) 2012 Kaj Magnus Lindberg (born 1979)
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

package debiki.dao

import actions.SafeActions.SessionRequest
import com.debiki.core._
import debiki.DebikiHttp.{throwNotFound, throwForbidden}
import java.net.InetAddress
import java.{util => ju}
import debiki.DebikiSecurity
import requests.ApiRequest
import scala.collection.immutable
import Prelude._
import EmailNotfPrefs.EmailNotfPrefs
import CachingDao.{CacheKey, CacheValueIgnoreVersion}


trait UserDao {
  self: SiteDao =>


  def insertInvite(invite: Invite) {
    readWriteTransaction { transaction =>
      transaction.insertInvite(invite)
    }
  }


  def acceptInviteCreateUser(secretKey: String): (CompleteUser, Invite) = {
    readWriteTransaction { transaction =>
      var invite = transaction.loadInvite(secretKey) getOrElse throwForbidden(
        "DwE6FKQ2", "Bad invite key")

      invite.acceptedAt foreach { acceptedAt =>
        val millisAgo = (new ju.Date).getTime - acceptedAt.getTime
        // For now: If the invitation is < 1 day old, allow the user to log in
        // again via the invitation link. In Discourse, this timeout is configurable.
        if (millisAgo < 24 * 3600 * 1000) {
          val user = loadCompleteUser(invite.userId getOrDie "DwE6FKEW2") getOrDie "DwE8KES2"
          return (user, invite)
        }

        throwForbidden("DwE0FKW2", "You have joined the site already, but this link has expired")
      }

      if (transaction.loadUserByEmailOrUsername(invite.emailAddress).isDefined)
        throwForbidden("DwE8KFG4", o"""You have joined this site already, so this
             join-site invitation link does nothing. Thanks for clicking it anyway""")

      val userId = transaction.nextAuthenticatedUserId
      var newUser = invite.makeUser(userId, transaction.currentTime)
      val inviter = transaction.loadUser(invite.createdById) getOrDie "DwE5FKG4"
      if (inviter.isStaff) {
        newUser = newUser.copy(
          isApproved = Some(true),
          approvedAt = Some(transaction.currentTime),
          approvedById = Some(invite.createdById))
      }

      invite = invite.copy(acceptedAt = Some(transaction.currentTime), userId = Some(userId))

      // COULD loop and append 1, 2, 3, ... until there's no username clash.
      transaction.insertAuthenticatedUser(newUser)
      transaction.updateInvite(invite)
      (newUser, invite)
    }
  }


  def approveUser(userId: UserId, approverId: UserId) {
    approveRejectUndoUser(userId, approverId = approverId, isapproved = Some(true))
  }


  def rejectUser(userId: UserId, approverId: UserId) {
    approveRejectUndoUser(userId, approverId = approverId, isapproved = Some(false))
  }


  def undoApproveOrRejectUser(userId: UserId, approverId: UserId) {
    approveRejectUndoUser(userId, approverId = approverId, isapproved = None)
  }


  private def approveRejectUndoUser(userId: UserId, approverId: UserId,
        isapproved: Option[Boolean]) {
    readWriteTransaction { transaction =>
      var user = transaction.loadTheCompleteUser(userId)
      user = user.copy(
        isApproved = isapproved,
        approvedAt = Some(transaction.currentTime),
        approvedById = Some(approverId))
      transaction.updateCompleteUser(user)
    }
    refreshUserInAnyCache(userId)
  }


  def setStaffFlags(userId: UserId, isAdmin: Option[Boolean] = None,
        isModerator: Option[Boolean] = None, changedById: UserId) {
    require(isAdmin.isDefined != isModerator.isDefined, "DwE4KEP20")
    if (userId == changedById)
      throwForbidden("DwE4KEF2", "Cannot change one's own is-admin and is-moderator state")

    readWriteTransaction { transaction =>
      var user = transaction.loadTheCompleteUser(userId)

      if (user.isSuspendedAt(transaction.currentTime) && (
          isAdmin == Some(true) || isModerator == Some(true)))
        throwForbidden("DwE2KEP8", "User is suspended")

      user = user.copy(
        isAdmin = isAdmin.getOrElse(user.isAdmin),
        isModerator = isModerator.getOrElse(user.isModerator))
      // COULD update audit log.
      transaction.updateCompleteUser(user)
    }
    refreshUserInAnyCache(userId)
  }


  def suspendUser(userId: UserId, numDays: Int, reason: String, suspendedById: UserId) {
    require(numDays >= 1, "DwE4PKF8")
    readWriteTransaction { transaction =>
      var user = transaction.loadTheCompleteUser(userId)
      if (user.isAdmin)
        throwForbidden("DwE4KEF24", "Cannot suspend admins")

      val suspendedTill = new ju.Date(transaction.currentTime.getTime + numDays * MillisPerDay)
      user = user.copy(
        suspendedAt = Some(transaction.currentTime),
        suspendedTill = Some(suspendedTill),
        suspendedById = Some(suspendedById),
        suspendedReason = Some(reason.trim))
      transaction.updateCompleteUser(user)
    }
    refreshUserInAnyCache(userId)
  }


  def unsuspendUser(userId: UserId) {
    readWriteTransaction { transaction =>
      var user = transaction.loadTheCompleteUser(userId)
      user = user.copy(suspendedAt = None, suspendedTill = None, suspendedById = None,
        suspendedReason = None)
      transaction.updateCompleteUser(user)
    }
    refreshUserInAnyCache(userId)
  }


  def blockGuest(postId: UniquePostId, numDays: Int, blockerId: UserId) {
    readWriteTransaction { transaction =>
      val auditLogEntry: AuditLogEntry = transaction.loadCreatePostAuditLogEntry(postId) getOrElse {
        throwForbidden("DwE2WKF5", "Cannot block user: No audit log entry, IP unknown")
      }

      if (!User.isGuestId(auditLogEntry.doerId))
        throwForbidden("DwE4WKQ2", "Cannot block authenticated users. Suspend them instead")

      val blockedTill =
        Some(new ju.Date(transaction.currentTime.getTime + MillisPerDay * numDays))

      val ipBlock = Block(
        ip = Some(auditLogEntry.browserIdData.inetAddress),
        browserIdCookie = None,
        blockedById = blockerId,
        blockedAt = transaction.currentTime,
        blockedTill = blockedTill)

      val browserIdCookieBlock = Block(
        ip = None,
        browserIdCookie = Some(auditLogEntry.browserIdData.idCookie),
        blockedById = blockerId,
        blockedAt = transaction.currentTime,
        blockedTill = blockedTill)

      // COULD catch dupl key error when inserting IP block, and continue anyway
      // with inserting browser id cookie block.
      transaction.insertBlock(ipBlock)
      transaction.insertBlock(browserIdCookieBlock)
    }
  }


  def unblockGuest(postNr: PostNr, unblockerId: UserId) {
    readWriteTransaction { transaction =>
      val auditLogEntry: AuditLogEntry = transaction.loadCreatePostAuditLogEntry(postNr) getOrElse {
        throwForbidden("DwE5FK83", "Cannot unblock guest: No audit log entry, IP unknown")
      }
      transaction.unblockIp(auditLogEntry.browserIdData.inetAddress)
      transaction.unblockBrowser(auditLogEntry.browserIdData.idCookie)
    }
  }


  def loadAuthorBlocks(postId: UniquePostId): immutable.Seq[Block] = {
    readOnlyTransaction { transaction =>
      val auditLogEntry = transaction.loadCreatePostAuditLogEntry(postId) getOrElse {
        return Nil
      }
      val browserIdData = auditLogEntry.browserIdData
      transaction.loadBlocks(ip = browserIdData.ip, browserIdCookie = browserIdData.idCookie)
    }
  }


  def loadBlocks(ip: String, browserIdCookie: String): immutable.Seq[Block] = {
    readOnlyTransactionNotSerializable { transaction =>
      transaction.loadBlocks(ip = ip, browserIdCookie = browserIdCookie)
    }
  }


  def createIdentityUserAndLogin(newUserData: NewUserData): LoginGrant = {
    readWriteTransaction { transaction =>
      val userId = transaction.nextAuthenticatedUserId
      val user = newUserData.makeUser(userId, transaction.currentTime)
      val identityId = transaction.nextIdentityId
      val identity = newUserData.makeIdentity(userId = userId, identityId = identityId)
      transaction.insertAuthenticatedUser(user)
      transaction.insertIdentity(identity)
      LoginGrant(Some(identity), user.briefUser, isNewIdentity = true, isNewRole = true)
    }
  }


  /** Used if a user without any matching identity has been created (e.g. because
    * you signup as an email + password user, or accept an invitation). And you then
    * later on try to login via e.g. a Gmail account with the same email address.
    * Then we want to create a Gmail OpenAuth identity and connect it to the user
    * in the database.
    */
  def createIdentityConnectToUserAndLogin(user: User, oauthDetails: OpenAuthDetails)
        : LoginGrant = {
    require(user.email.nonEmpty, "DwE3KEF7")
    require(user.emailVerifiedAt.nonEmpty, "DwE5KGE2")
    require(user.isAuthenticated, "DwE4KEF8")
    readWriteTransaction { transaction =>
      val identityId = transaction.nextIdentityId
      val identity = OpenAuthIdentity(id = identityId, userId = user.id, oauthDetails)
      transaction.insertIdentity(identity)
      LoginGrant(Some(identity), user, isNewIdentity = true, isNewRole = false)
    }
  }


  def createPasswordUserCheckPasswordStrong(userData: NewPasswordUserData): User = {
    DebikiSecurity.throwErrorIfPasswordTooWeak(
      password = userData.password, username = userData.username,
      fullName = userData.name, email = userData.email)
    readWriteTransaction { transaction =>
      val userId = transaction.nextAuthenticatedUserId
      val user = userData.makeUser(userId, transaction.currentTime)
      transaction.insertAuthenticatedUser(user)
      user.briefUser
    }
  }


  def changePasswordCheckStrongEnough(userId: UserId, newPassword: String): Boolean = {
    val newPasswordSaltHash = DbDao.saltAndHashPassword(newPassword)
    readWriteTransaction { transaction =>
      var user = transaction.loadTheCompleteUser(userId)
      DebikiSecurity.throwErrorIfPasswordTooWeak(
        password = newPassword, username = user.username,
        fullName = user.fullName, email = user.emailAddress)
      user = user.copy(passwordHash = Some(newPasswordSaltHash))
      transaction.updateCompleteUser(user)
    }
  }


  def loginAsGuest(loginAttempt: GuestLoginAttempt): User = {
    readWriteTransaction { transaction =>
      transaction.loginAsGuest(loginAttempt).user
    }
  }


  def tryLogin(loginAttempt: LoginAttempt): LoginGrant = {
    readWriteTransaction { transaction =>
      val loginGrant = transaction.tryLogin(loginAttempt)
      if (!loginGrant.user.isSuspendedAt(loginAttempt.date))
        return loginGrant

      val user = transaction.loadCompleteUser(loginGrant.user.id) getOrElse throwForbidden(
        "DwE05KW2", "User not found, id: " + loginGrant.user.id)
      // Still suspended?
      if (user.suspendedAt.isDefined) {
        val forHowLong = user.suspendedTill match {
          case None => "forever"
          case Some(date) => "until " + toIso8601(date)
        }
        throwForbidden("DwE403SP0", o"""Account suspended $forHowLong,
            reason: ${user.suspendedReason getOrElse "?"}""")
      }
      loginGrant
    }
  }


  def loadUsers(): immutable.Seq[User] = {
    readOnlyTransaction { transaction =>
      transaction.loadUsers()
    }
  }


  def loadCompleteUsers(onlyThosePendingApproval: Boolean): immutable.Seq[CompleteUser] = {
    readOnlyTransaction { transaction =>
      transaction.loadCompleteUsers(onlyPendingApproval = onlyThosePendingApproval)
    }
  }


  def loadCompleteUser(userId: UserId): Option[CompleteUser] = {
    readOnlyTransaction { transaction =>
      transaction.loadCompleteUser(userId)
    }
  }


  def loadUser(userId: UserId): Option[User] = {
    readOnlyTransaction { transaction =>
      transaction.loadUser(userId)
    }
  }


  def loadUserByEmailOrUsername(emailOrUsername: String): Option[User] = {
    readOnlyTransaction { transaction =>
      // Don't need to cache this? Only called when logging in.
      transaction.loadUserByEmailOrUsername(emailOrUsername)
    }
  }


  def loadUserAndAnyIdentity(userId: UserId): Option[(Option[Identity], User)] = {
    loadIdtyDetailsAndUser(userId) match {
      case Some((identity, user)) => Some((Some(identity), user))
      case None =>
        // No OAuth or OpenID identity, try load password user:
        loadUser(userId) match {
          case Some(user) =>
            Some((None, user))
          case None =>
            None
        }
    }
  }


  private def loadIdtyDetailsAndUser(userId: UserId): Option[(Identity, User)] = {
    // Don't cache this, because this function is rarely called
    // — currently only when creating new website.
    readOnlyTransaction { transaction =>
      transaction.loadIdtyDetailsAndUser(userId)
    }
  }


  def loadUserInfoAndStats(userId: UserId): Option[UserInfoAndStats] =
    siteDbDao.loadUserInfoAndStats(userId)


  def listUserActions(userId: UserId): Seq[UserActionInfo] =
    siteDbDao.listUserActions(userId)


  def loadPermsOnPage(reqInfo: PermsOnPageQuery): PermsOnPage =
    // Currently this results in no database request; there's nothing to cache.
    siteDbDao.loadPermsOnPage(reqInfo)


  def loadPermsOnPage(request: ApiRequest[_], pageId: PageId): PermsOnPage = {
    val pageMeta = loadPageMeta(pageId)
    val pagePath = lookupPagePath(pageId) getOrElse throwNotFound(
      "DwE74BK0", s"No page path found to page id: $pageId")

    siteDbDao.loadPermsOnPage(PermsOnPageQuery(
      tenantId = request.tenantId,
      ip = request.ip,
      user = request.user,
      pagePath = pagePath,
      pageMeta = pageMeta))
  }


  def verifyEmail(userId: UserId, verifiedAt: ju.Date) {
    readWriteTransaction { transaction =>
      var user = transaction.loadTheCompleteUser(userId)
      user = user.copy(emailVerifiedAt = Some(verifiedAt))
      transaction.updateCompleteUser(user)
    }
    refreshUserInAnyCache(userId)
  }


  def setUserAvatar(userId: UserId, tinyAvatar: Option[UploadRef], smallAvatar: Option[UploadRef],
        mediumAvatar: Option[UploadRef], browserIdData: BrowserIdData) {
    require(smallAvatar.isDefined == tinyAvatar.isDefined, "EsE9PYM2")
    require(smallAvatar.isDefined == mediumAvatar.isDefined, "EsE8YFM2")
    readWriteTransaction { transaction =>
      val userBefore = transaction.loadTheCompleteUser(userId)
      val userAfter = userBefore.copy(
        tinyAvatar = tinyAvatar,
        smallAvatar = smallAvatar,
        mediumAvatar = mediumAvatar)

      val hasNewAvatar =
        userBefore.tinyAvatar != userAfter.tinyAvatar ||
          userBefore.smallAvatar != userAfter.smallAvatar ||
          userBefore.mediumAvatar != userAfter.mediumAvatar

      val relevantRefs =
        if (!hasNewAvatar) Set.empty
        else
          userBefore.tinyAvatar.toSet ++ userBefore.smallAvatar.toSet ++
            userBefore.mediumAvatar.toSet ++ userAfter.tinyAvatar.toSet ++
            userAfter.smallAvatar.toSet ++ userAfter.mediumAvatar.toSet
      val refsInUseBefore = transaction.filterUploadRefsInUse(relevantRefs)

      transaction.updateCompleteUser(userAfter)

      if (hasNewAvatar) {
        val refsInUseAfter = transaction.filterUploadRefsInUse(relevantRefs)
        val refsAdded = refsInUseAfter -- refsInUseBefore
        val refsRemoved = refsInUseBefore -- refsInUseAfter
        refsAdded.foreach(transaction.updateUploadQuotaUse(_, wasAdded = true))
        refsRemoved.foreach(transaction.updateUploadQuotaUse(_, wasAdded = false))

        userBefore.tinyAvatar.foreach(transaction.updateUploadedFileReferenceCount)
        userBefore.smallAvatar.foreach(transaction.updateUploadedFileReferenceCount)
        userBefore.mediumAvatar.foreach(transaction.updateUploadedFileReferenceCount)
        userAfter.tinyAvatar.foreach(transaction.updateUploadedFileReferenceCount)
        userAfter.smallAvatar.foreach(transaction.updateUploadedFileReferenceCount)
        userAfter.mediumAvatar.foreach(transaction.updateUploadedFileReferenceCount)
        transaction.markPagesWithUserAvatarAsStale(userId)
      }
    }
    refreshUserInAnyCache(userId)
    // Clear the PageStuff cache (by clearing the whole in-mem cache), because
    // PageStuff includes avatar urls.
    // COULD have above markPagesWithUserAvatarAsStale() return a page id list and
    // uncache only those pages.
    emptyCache()
  }


  def configRole(userId: RoleId,
        emailNotfPrefs: Option[EmailNotfPrefs] = None, isAdmin: Option[Boolean] = None,
        isOwner: Option[Boolean] = None) {
    // Don't specify emailVerifiedAt here — use verifyEmail() instead; it refreshes the cache.
    readWriteTransaction { transaction =>
      var user = transaction.loadTheCompleteUser(userId)
      emailNotfPrefs foreach { prefs =>
        user = user.copy(emailNotfPrefs = prefs)
      }
      isAdmin foreach { isAdmin =>
        user = user.copy(isAdmin = isAdmin)
      }
      isOwner foreach { isOwner =>
        user = user.copy(isOwner = isOwner)
      }
      transaction.updateCompleteUser(user)
    }
    refreshUserInAnyCache(userId)
  }


  def configIdtySimple(ctime: ju.Date, emailAddr: String, emailNotfPrefs: EmailNotfPrefs) = {
    siteDbDao.configIdtySimple(ctime = ctime,
      emailAddr = emailAddr, emailNotfPrefs = emailNotfPrefs)
    // COULD refresh guest in cache: new email prefs --> perhaps show "??" not "?" after name.
  }


  def listUsers(): Seq[User] = {
    readOnlyTransaction { transaction =>
      transaction.loadUsers()
    }
  }


  def listUsernames(pageId: PageId, prefix: String): Seq[NameAndUsername] =
    siteDbDao.listUsernames(pageId = pageId, prefix = prefix)


  def loadUserIdsWatchingPage(pageId: PageId): Seq[UserId] =
    siteDbDao.loadUserIdsWatchingPage(pageId)


  def loadRolePageSettings(roleId: RoleId, pageId: PageId): RolePageSettings =
    siteDbDao.loadRolePageSettings(roleId = roleId, pageId = pageId) getOrElse
      RolePageSettings.Default


  def saveRolePageSettings(roleId: RoleId, pageId: PageId, settings: RolePageSettings) =
    siteDbDao.saveRolePageSettings(roleId = roleId, pageId = pageId, settings)


  def saveRolePreferences(preferences: UserPreferences) = {
    // BUG: the lost update bug.
    readWriteTransaction { transaction =>
      var user = transaction.loadTheCompleteUser(preferences.userId)
      user = user.copyWithNewPreferences(preferences)
      transaction.updateCompleteUser(user)
    }
    refreshUserInAnyCache(preferences.userId)
  }


  def saveGuest(guestId: UserId, name: String, url: String): Unit = {
    // BUG: the lost update bug.
    readWriteTransaction { transaction =>
      var guest = transaction.loadTheUser(guestId)
      guest = guest.copy(displayName = name, website = url)
      transaction.updateGuest(guest)
    }
    refreshUserInAnyCache(guestId)
  }


  def perhapsBlockGuest(request: SessionRequest[_]) {
    if (request.underlying.method == "GET")
      return

    // Authenticated users are ignored here. Suspend them instead.
    if (request.sidStatus.userId.map(User.isRoleId) == Some(true))
      return

    // Ignore not-logged-in people, unless they attempt to login as guests.
    if (request.sidStatus.userId.isEmpty) {
      val guestLoginPath = controllers.routes.LoginAsGuestController.loginGuest().url
      if (!request.path.contains(guestLoginPath))
        return
    }

    if (request.browserId.isEmpty)
      throwForbidden("DwE403NBI0", "No browser id cookie")

    // COULD cache blocks, but not really needed since this is for post requests only.
    val blocks = loadBlocks(
      ip = request.remoteAddress,
      browserIdCookie = request.browserId.get.cookieValue)

    val nowMillis = System.currentTimeMillis
    for (block <- blocks) {
      if (block.isActiveAt(nowMillis)) {
        throwForbidden(
          "DwE403BK01", "Not allowed. Please authenticate yourself by creating a real account.")
      }
    }
  }


  def refreshUserInAnyCache(userId: UserId) {
  }

}



trait CachingUserDao extends UserDao {
  self: CachingSiteDao =>


  override def createIdentityUserAndLogin(newUserData: NewUserData): LoginGrant = {
    val loginGrant = super.createIdentityUserAndLogin(newUserData)
    fireUserCreated(loginGrant.user)
    loginGrant
  }


  override def createPasswordUserCheckPasswordStrong(userData: NewPasswordUserData): User = {
    val user = super.createPasswordUserCheckPasswordStrong(userData)
    fireUserCreated(user)
    user
  }


  override def loginAsGuest(loginAttempt: GuestLoginAttempt): User = {
    val user = super.loginAsGuest(loginAttempt)
    putInCache(
      key(user.id),
      CacheValueIgnoreVersion(user))
    user
  }


  override def tryLogin(loginAttempt: LoginAttempt): LoginGrant = {
    // Don't save any site cache version, because user specific data doesn't change
    // when site specific data changes.
    val loginGrant = super.tryLogin(loginAttempt)
    putInCache(
      key(loginGrant.user.id),
      CacheValueIgnoreVersion(loginGrant.user))
    loginGrant
  }


  override def loadUser(userId: UserId): Option[User] = {
    lookupInCache[User](
      key(userId),
      orCacheAndReturn = super.loadUser(userId),
      ignoreSiteCacheVersion = true)
  }


  override def refreshUserInAnyCache(userId: UserId) {
    removeFromCache(key(userId))
  }


  private def key(userId: UserId) = CacheKey(siteId, s"$userId|UserById")

}


