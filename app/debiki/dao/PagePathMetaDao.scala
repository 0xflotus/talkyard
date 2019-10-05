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

import com.debiki.core._
import com.debiki.core.Prelude._
import scala.collection.immutable
import scala.collection.mutable



trait PagePathMetaDao {
  self: SiteDao =>

  memCache.onPageSaved { sitePageId =>
    uncacheMetaAndAncestors(sitePageId)
  }


  /*
  def movePages(pageIds: Seq[PageId], fromFolder: String, toFolder: String) =
    siteDbDao.movePages(pageIds, fromFolder = fromFolder, toFolder = toFolder)
    */


  def moveRenamePage(pageId: PageId, newFolder: Option[String] = None,
        showId: Option[Boolean] = None, newSlug: Option[String] = None): PagePathWithId = {
    _removeCachedPathsTo(pageId)
    val newPath = readWriteTransaction(_.moveRenamePage(pageId = pageId, newFolder = newFolder,
      showId = showId, newSlug = newSlug))

    // I don't know how this could happen, but in case there's already an
    // entry that maps `newPath` to something, remove it.
    memCache.remove(_pathWithIdByPathKey(newPath.toOld(siteId)))

    memCache.firePageMoved(newPath.toOld(siteId))
    newPath
  }


  def checkPagePath(pathToCheck: PagePath): Option[PagePath] = {
    val key = _pathWithIdByPathKey(pathToCheck)
    memCache.lookup[PagePath](key) foreach { path =>
      return Some(path)
    }
    val siteCacheVersion = memCache.siteCacheVersionNow()
    readOnlyTransaction(_.checkPagePath(pathToCheck)) map { correctPath =>
      // Don't cache non-exact paths if page id shown, since there are
      // infinitely many such paths.
      // Performance, caching: COULD let checkPagePath() clarify whether
      // pathToCheck was actually found in the database (in DW1_PAGE_PATHS),
      // and cache it, if found, regardless of if id shown in url.
      // Or better & much simpler: Cache SitePageId —> correctPath.
      if (!pathToCheck.showId || correctPath.value == pathToCheck.value)
        memCache.put(key, MemCacheItem(correctPath, siteCacheVersion))
      return Some(correctPath)
    }
    None
  }


  def getPagePath(pageId: PageId): Option[PagePath] = {
    memCache.lookup(
      _pathByPageIdKey(pageId),
      orCacheAndReturn =
        readOnlyTransaction(_.loadPagePath(pageId) .map(_.toOld(siteId))))
  }


  def loadThePageMeta(pageId: PageId): PageMeta =
    readOnlyTransaction(_.loadThePageMeta(pageId))


  def getThePageMeta(pageId: PageId): PageMeta =
    getPageMeta(pageId) getOrElse {
      throw PageNotFoundException(pageId)
    }


  def getPageMeta(pageId: PageId): Option[PageMeta] = {
    memCache.lookup[PageMeta](
      pageMetaByIdKey(SitePageId(siteId, pageId)),
      orCacheAndReturn = readOnlyTransaction(_.loadPageMeta(pageId)))
  }


  def getPageMetasAsMap(pageIds: Set[PageId], anySiteId: Option[SiteId] = None)
        : Map[PageId, PageMeta] = {
    // Somewhat dupl code [5KWE02], PageStuffDao.getPageStuffById() is similar.
    // Break out helper function getManyById[K, V](keys) ?

    val pageMetas = mutable.ArrayBuffer[PageMeta]()
    val missingIds = mutable.HashSet[PageId]()

    pageIds foreach { pageId =>
      memCache.lookup[PageMeta](pageMetaByIdKey(SitePageId(siteId, pageId))) match {
        case Some(pageMeta) =>
          pageMetas.append(pageMeta)
        case None =>
          missingIds.add(pageId)
      }
    }

    if (missingIds.nonEmpty) {
      val siteCacheVersion = memCache.siteCacheVersionNow()
      val remainingPageMetas = readOnlyTransaction(_.loadPageMetas(missingIds))
      remainingPageMetas foreach { pageMeta =>
        memCache.put(pageMetaByIdKey(SitePageId(siteId, pageMeta.pageId)),
          MemCacheItem(pageMeta, siteCacheVersion))
      }
      pageMetas.appendAll(remainingPageMetas)
    }

    immutable.HashMap[PageId, PageMeta](pageMetas map { pageMeta =>
      pageMeta.pageId -> pageMeta
    }: _*)
  }


  def getPagePathAndMeta(pageId: PageId): Option[PagePathAndMeta] = {
    // I don't think writing a dedicated SQL query that does this in one
    // roundtrip is worth the trouble? Won't work with NoSQL databases anyway?
    val anyMeta = getPageMeta(pageId)
    val anyPath = getPagePath(pageId)
    for (meta <- anyMeta; path <- anyPath)
      yield PagePathAndMeta(path, meta)
  }


  def getThePageMetaForPostId(postId: PostId): PageMeta = {
    getPageMetaForPostId(postId) getOrElse throwNoSuchElem("EdE2WKG4", s"Post $siteId:$postId not found")
  }


  def getPageMetaForPostId(postId: PostId): Option[PageMeta] = {
    // Currently not cached.
    COULD_OPTIMIZE // write query that directly loads page meta by post id, one single request.
    readOnlyTransaction { tx =>
      val post = tx.loadPost(postId) getOrElse {
        return None
      }
      tx.loadPageMeta(post.pageId)
    }
  }


  def getRealPageId(altPageId: AltPageId): Option[PageId] = {
    memCache.lookup(
      realPageIdByAltIdKey(altPageId),
      orCacheAndReturn =
        readOnlyTransaction(_.loadRealPageId(altPageId)))
  }


  def getAltPageIdsForPageId(realPageId: PageId): Set[AltPageId] = {
    COULD_OPTIMIZE // cache this?  [306FKTGP03]
    readOnlyTransaction { tx =>
      tx.listAltPageIds(realPageId)
    }
  }


  private def _removeCachedPathsTo(pageId: PageId) {
    // Remove cache entries from id to path,
    // and from a browser's specified path to the correct path with id.
    readOnlyTransaction(_.loadPagePath(pageId)) foreach { path =>
      memCache.remove(_pathWithIdByPathKey(path.toOld(siteId)))
      memCache.remove(_pathByPageIdKey(pageId))
    }

    // ---- Obsolete comment:
    // Could also remove paths similar to `oldPath` but with a trailing
    // slash added or removed, because `checkPagePath` might have
    // remembered that e.g. /-pageid maps to `oldPath`.
    // Could also remove any /-pageid path.
    // This feels rather unimporant though.
    // The effect should be that incorrect page paths might return
    // 404 Not Found until the server has been restarted?
    // ----
  }


  private def uncacheMetaAndAncestors(sitePageId: SitePageId) {
    memCache.remove(ancestorIdsKey(sitePageId))
    memCache.remove(pageMetaByIdKey(sitePageId))
  }


  private def _pathWithIdByPathKey(pagePath: PagePath) =
    MemCacheKey(siteId, s"${pagePath.value}|PagePathByPath")

  private def _pathByPageIdKey(pageId: PageId) =
    MemCacheKey(siteId, s"$pageId|PagePathById")

  private def pageMetaByIdKey(sitePageId: SitePageId) =
    MemCacheKey(sitePageId.siteId, s"${sitePageId.pageId}|PageMetaById")

  private def ancestorIdsKey(sitePageId: SitePageId) =
    MemCacheKey(sitePageId.siteId, s"${sitePageId.pageId}|AncestorIdsById")

  private def realPageIdByAltIdKey(altPageId: AltPageId) =
    MemCacheKey(siteId, s"$altPageId|RealIdByAltId")

}

