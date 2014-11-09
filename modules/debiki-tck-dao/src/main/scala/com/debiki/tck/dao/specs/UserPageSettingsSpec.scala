/**
 * Copyright (C) 2014 Kaj Magnus Lindberg (born 1979)
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


package com.debiki.tck.dao.specs

import com.debiki.core._
import com.debiki.core.Prelude._
import com.debiki.tck.dao._
import com.debiki.tck.dao.code._
import java.{util => ju}
import org.scalatest._


class UserPageSettingsSpec(daoFactory: DbDaoFactory) extends DbDaoSpec(daoFactory) {

  lazy val utils = new TestUtils(daoFactory)
  lazy val site = utils.createFirstSite()
  lazy val siteUtils = new SiteTestUtils(site, daoFactory)
  def dao = siteUtils.dao


  "RolePageSettingsSpec can" - {

    var page: PageNoPath = null
    var passwordRole: User = null
    var passwordLoginGrant: LoginGrant = null

    "find no page settings for non-existing user and page" in {
      dao.loadRolePageSettings(roleId = "dummy", pageId = "dummy") mustBe None
    }

    "create a password role and a page, find no page settings" in {
      passwordRole = siteUtils.createPasswordRole()
      passwordLoginGrant = siteUtils.login(passwordRole)
      page = siteUtils.createPageAndBody(
        passwordLoginGrant, PageRole.ForumTopic, "Page text.").withoutPath
      dao.loadRolePageSettings(roleId = passwordRole.id, pageId = page.id) mustBe None
    }

    "save and find settings" in {
      val settings = RolePageSettings(PageNotfLevel.Tracking)
      dao.saveRolePageSettings(roleId = passwordRole.id, pageId = page.id, settings)
      dao.loadRolePageSettings(roleId = passwordRole.id, pageId = page.id) mustBe Some(settings)
    }

    "update the settings" in {
      val newSettings = RolePageSettings(PageNotfLevel.Watching)
      dao.saveRolePageSettings(roleId = passwordRole.id, pageId = page.id, newSettings)
      dao.loadRolePageSettings(roleId = passwordRole.id, pageId = page.id) mustBe Some(newSettings)
    }

    "not find the page setting for another user or page" in {
      dao.loadRolePageSettings(roleId = "dummy", pageId = "dummy") mustBe None
    }

  }

}

