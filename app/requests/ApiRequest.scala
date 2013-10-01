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

package requests

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki._
import debiki.dao.SiteDao
import java.{util => ju}
import play.api.mvc.{Action => _, _}


/**
 * A request that's not related to any particular page.
 */
case class ApiRequest[A](
  sid: SidStatus,
  xsrfToken: XsrfOk,
  identity: Option[Identity],
  user: Option[User],
  dao: SiteDao,
  request: Request[A]) extends DebikiRequest[A] {
}

