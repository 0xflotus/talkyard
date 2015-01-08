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


package com.debiki.core



case class RawSettings(target: SettingsTarget, valuesBySettingName: Map[String, Any])



/** Identifies a section of all pages, e.g. a forum, a subforum, a blog, or a single page.
  * Used to clarify what pages a setting should affect.
  */
sealed abstract class SettingsTarget

object SettingsTarget {
  case object WholeSite extends SettingsTarget
  case class PageTree(rootPageId: PageId) extends SettingsTarget
  case class SinglePage(id: PageId) extends SettingsTarget
}

