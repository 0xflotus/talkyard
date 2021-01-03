/**
 * Copyright (c) 2021 Debiki AB
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

package talkyard.server.linkpreview


import org.scalatest._
import org.scalatest.matchers.must
import com.debiki.core.Prelude._
import com.debiki.core._
import debiki.onebox.engines.TwitterPrevwRendrEng


class LinkPreviewRendererSpec extends FreeSpec with must.Matchers {

  import debiki.onebox.LinkPreviewRenderer.tweakLinks

  "LinkPreviewRenderer.tweakLinks can" - {
    "leave non-links as is" in {
      tweakLinks("", toHttps = true, uploadsUrlCdnPrefix = None, NoSiteId, "") mustBe ""
      tweakLinks("abc", toHttps = true, uploadsUrlCdnPrefix = None, NoSiteId, ""
            ) mustBe "abc"

      // Not a link — just plain text:
      tweakLinks("http://ex.co", toHttps = true, uploadsUrlCdnPrefix = None, NoSiteId, ""
            ) mustBe "http://ex.co"
    }


    "change to https" in {
      def htmlWithLink(https: Bo): St = {
        val scheme = if (https) "https" else "http"
        o"""bla blah
        <a href="$scheme://ex.co">http://not.link.co</a>
        <img src="$scheme://ex.co">
        """
      }

      tweakLinks(htmlWithLink(false), toHttps = true,
            uploadsUrlCdnPrefix = None, NoSiteId, "") mustBe htmlWithLink(true)

      tweakLinks(htmlWithLink(false), toHttps = false,
            uploadsUrlCdnPrefix = None, NoSiteId, "") mustBe htmlWithLink(false)
    }


    "re-point to CDN" in {
      def htmlWithUplLink(https: Bo, uplPath: St = "/-/u/"): St = {
        val scheme = if (https) "https:" else "http:"
        o"""bla blah
        <img src="${uplPath}3/a/bc/defg1234.jpg">
        bla2 blah2
        """
      }

      tweakLinks(htmlWithUplLink(https = false), toHttps = false,
            uploadsUrlCdnPrefix = None, NoSiteId, "") mustBe htmlWithUplLink(false)

      tweakLinks(htmlWithUplLink(https = false), toHttps = false,
            uploadsUrlCdnPrefix = Some("https://cdn.ex.co/-/u/"), NoSiteId, ""
            ) mustBe htmlWithUplLink(false, "https://cdn.ex.co/-/u/")
    }
  }

}


