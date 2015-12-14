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

package controllers

import actions.ApiActions.{GetAction, StaffGetAction, StaffPostJsonAction}
import debiki.dao.PageStuff
import collection.mutable
import com.debiki.core._
import com.debiki.core.Prelude._
import debiki._
import debiki.ReactJson._
import java.{util => ju}
import play.api.mvc
import play.api.libs.json._
import play.api.mvc.{Action => _, _}
import requests.DebikiRequest
import scala.collection.mutable.ArrayBuffer
import scala.util.Try
import Utils.OkSafeJson
import Utils.ValidationImplicits._
import DebikiHttp.{throwBadReq, throwBadRequest, throwNotFound}


/** Handles requests related to forums and forum categories.
 */
object ForumController extends mvc.Controller {

  /** Keep synced with client/forum/list-topics/ListTopicsController.NumNewTopicsPerRequest. */
  val NumTopicsToList = 40


  def createForum = StaffPostJsonAction(maxLength = 200) { request =>
    val title = (request.body \ "title").as[String]
    val folder = (request.body \ "folder").as[String]
    val pagePath = request.dao.createForum(title, folder = folder,
      creatorId = request.theUserId, request.theBrowserIdData)
    OkSafeJson(JsString(pagePath.value))
  }


  def loadCategory(id: String) = StaffGetAction { request =>
    val categoryId = Try(id.toInt) getOrElse throwBadRequest("DwE6PU1", "Invalid category id")
    val category = request.dao.loadTheCategory(categoryId)
    val json = categoryToJson(category, recentTopics = Nil, pageStuffById = Map.empty)
    OkSafeJson(json)
  }


  def saveCategory = StaffPostJsonAction(maxLength = 1000) { request =>
    val body = request.body
    val sectionPageId = (body \ "sectionPageId").as[PageId]
    val hideInForum = (body \ "hideInForum").asOpt[Boolean].getOrElse(false)
    val newTopicTypeInts = (body \ "newTopicTypes").as[List[Int]]
    val newTopicTypes = newTopicTypeInts map { typeInt =>
      PageRole.fromInt(typeInt) getOrElse throwBadReq(
        "DwE7KUP3", s"Bad new topic type int: $typeInt")
    }

    val categoryData = CreateEditCategoryData(
      anyId = (body \ "categoryId").asOpt[CategoryId],
      sectionPageId = sectionPageId,
      parentId = (body \ "parentCategoryId").as[CategoryId],
      name = (body \ "name").as[String],
      slug = (body \ "slug").as[String],
      position = (body \ "position").as[Int],
      newTopicTypes = newTopicTypes,
      hideInForum = hideInForum)

    import Category._

    if (categoryData.name.isEmpty)
      throwBadRequest("EsE5JGKU1", s"Please type a category name")

    if (categoryData.name.length > MaxNameLength)
      throwBadRequest("EsE8RSUY0", s"Too long category name: '${categoryData.name}'")

    if (categoryData.slug.isEmpty)
      throwBadRequest("EsE4PKL6", s"Please type a category slug")

    if (categoryData.slug.length > MaxSlugLength)
      throwBadRequest("EsE9MFU4", s"Too long category slug: '${categoryData.slug}'")

    if (categoryData.newTopicTypes.size > MaxTopicTypes)
      throwBadRequest("EsE1GKU6", s"Too many topic types")

    val category = categoryData.anyId match {
      case Some(categoryId) =>
        request.dao.editCategory(categoryData, editorId = request.theUserId,
          request.theBrowserIdData)
      case None =>
        val (category, _) = request.dao.createCategory(
          categoryData, creatorId = request.theUserId, request.theBrowserIdData)
        category
    }

    OkSafeJson(Json.obj(
      "allCategories" -> ReactJson.categoriesJson(category.sectionPageId,
        includeHiddenInForum = true, request.dao),
      "newCategoryId" -> category.id,
      "newCategorySlug" -> category.slug))
  }


  def listTopics(categoryId: String) = GetAction { request =>
    val categoryIdInt: CategoryId = Try(categoryId.toInt) getOrElse throwBadReq(
      "DwE4KG08", "Bat category id")
    val pageQuery: PageQuery = parseThePageQuery(request)
    val topics = listTopicsInclPinned(categoryIdInt, pageQuery, request.dao,
      includeDescendantCategories = true, includeHiddenInForum = request.isStaff)
    val pageStuffById = request.dao.loadPageStuff(topics.map(_.pageId))
    val topicsJson: Seq[JsObject] = topics.map(topicToJson(_, pageStuffById))
    val json = Json.obj("topics" -> topicsJson)
    OkSafeJson(json)
  }


  def listCategories(forumId: PageId) = GetAction { request =>
    val categories = request.dao.listSectionCategories(forumId,
      includeHiddenInForum = request.isStaff)
    val json = JsArray(categories.map({ category =>
      categoryToJson(category, recentTopics = Nil, pageStuffById = Map.empty)
    }))
    OkSafeJson(json)
  }


  def listCategoriesAndTopics(forumId: PageId) = GetAction { request =>
    val categories = request.dao.listSectionCategories(forumId,
      includeHiddenInForum = request.isStaff)

    val recentTopicsByCategoryId =
      mutable.Map[CategoryId, Seq[PagePathAndMeta]]()

    val pageIds = ArrayBuffer[PageId]()
    val pageQuery = PageQuery(PageOrderOffset.ByBumpTime(None), parsePageFilter(request))

    for (category <- categories) {
      val recentTopics = listTopicsInclPinned(category.id, pageQuery, request.dao,
        includeDescendantCategories = true, includeHiddenInForum = request.isStaff, limit = 6)
      recentTopicsByCategoryId(category.id) = recentTopics
      pageIds.append(recentTopics.map(_.pageId): _*)
    }

    val pageStuffById: Map[PageId, debiki.dao.PageStuff] =
      request.dao.loadPageStuff(pageIds)

    val json = JsArray(categories.map({ category =>
      categoryToJson(category, recentTopicsByCategoryId(category.id), pageStuffById)
    }))

    OkSafeJson(json)
  }


  def listTopicsInclPinned(categoryId: CategoryId, pageQuery: PageQuery, dao: debiki.dao.SiteDao,
        includeDescendantCategories: Boolean, includeHiddenInForum: Boolean,
        limit: Int = NumTopicsToList)
        : Seq[PagePathAndMeta] = {
    val topics: Seq[PagePathAndMeta] = dao.listPagesInCategory(
      categoryId, includeDescendantCategories, includeHiddenInForum = includeHiddenInForum,
      pageQuery, limit)

    // If sorting by bump time, sort pinned topics first. Otherwise, don't.
    val topicsInclPinned = pageQuery.orderOffset match {
      case orderOffset: PageOrderOffset.ByBumpTime if orderOffset.offset.isEmpty =>
        val pinnedTopics = dao.listPagesInCategory(
          categoryId, includeDescendantCategories, includeHiddenInForum = includeHiddenInForum,
          PageQuery(PageOrderOffset.ByPinOrderLoadOnlyPinned, pageQuery.pageFilter), limit)
        val notPinned = topics.filterNot(topic => pinnedTopics.exists(_.id == topic.id))
        val topicsSorted = (pinnedTopics ++ notPinned) sortBy { topic =>
          val meta = topic.meta
          val pinnedGlobally = meta.pinWhere.contains(PinPageWhere.Globally)
          val pinnedInThisCategory = meta.isPinned && meta.categoryId.contains(categoryId)
          val isPinned = pinnedGlobally || pinnedInThisCategory
          if (isPinned) topic.meta.pinOrder.get // 1..100
          else Long.MaxValue - topic.meta.bumpedOrPublishedOrCreatedAt.getTime // much larger
        }
        topicsSorted
      case _ => topics
    }

    topicsInclPinned
  }


  def parseThePageQuery(request: DebikiRequest[_]): PageQuery =
    parsePageQuery(request) getOrElse throwBadRequest(
      "DwE2KTES7", "No sort-order-offset specified")


  def parsePageQuery(request: DebikiRequest[_]): Option[PageQuery] = {
    val sortOrderStr = request.queryString.getFirst("sortOrder") getOrElse { return None }
    def anyDateOffset = request.queryString.getLong("epoch") map (new ju.Date(_))
    def anyNumOffset = request.queryString.getInt("num")

    val orderOffset: PageOrderOffset = sortOrderStr match {
      case "ByBumpTime" =>
        PageOrderOffset.ByBumpTime(anyDateOffset)
      case "ByLikesAndBumpTime" =>
        (anyNumOffset, anyDateOffset) match {
          case (Some(num), Some(date)) =>
            PageOrderOffset.ByLikesAndBumpTime(Some(num, date))
          case (None, None) =>
            PageOrderOffset.ByLikesAndBumpTime(None)
          case _ =>
            throwBadReq("DwE4KEW21", "Please specify both 'num' and 'epoch' or none at all")
        }
      case x => throwBadReq("DwE05YE2", s"Bad sort order: `$x'")
    }

    val filter = parsePageFilter(request)
    Some(PageQuery(orderOffset, filter))
  }


  def parsePageFilter(request: DebikiRequest[_]): PageFilter =
    request.queryString.getFirst("filter") match {
      case None => PageFilter.ShowAll
      case Some("ShowAll") => PageFilter.ShowAll
      case Some("ShowWaiting") => PageFilter.ShowWaiting
      case Some(x) => throwBadRequest("DwE5KGP8", s"Bad topic filter: $x")
    }


  private def categoryToJson(category: Category, recentTopics: Seq[PagePathAndMeta],
      pageStuffById: Map[PageId, debiki.dao.PageStuff]): JsObject = {
    require(recentTopics.isEmpty || pageStuffById.nonEmpty, "DwE8QKU2")
    val topicsNoAboutCategoryPage = recentTopics.filter(_.pageRole != PageRole.AboutCategory)
    val recentTopicsJson = topicsNoAboutCategoryPage.map(topicToJson(_, pageStuffById))
    ReactJson.categoryJson(category, recentTopicsJson)
  }


  def topicToJson(topic: PagePathAndMeta, pageStuffById: Map[PageId, PageStuff]): JsObject = {
    val topicStuff = pageStuffById.get(topic.pageId) getOrDie "DwE1F2I7"
    Json.obj(
      "pageId" -> topic.id,
      "pageRole" -> topic.pageRole.toInt,
      "title" -> topicStuff.title,
      "url" -> topic.path.value,
      "categoryId" -> topic.categoryId.getOrDie(
        "DwE49Fk3", s"Topic `${topic.id}', site `${topic.path.siteId}', belongs to no category"),
      "pinOrder" -> JsNumberOrNull(topic.meta.pinOrder),
      "pinWhere" -> JsNumberOrNull(topic.meta.pinWhere.map(_.toInt)),
      "excerpt" -> JsStringOrNull(topicStuff.bodyExcerptIfPinned),
      "numPosts" -> JsNumber(topic.meta.numRepliesVisible + 1),
      "numLikes" -> topic.meta.numLikes,
      "numWrongs" -> topic.meta.numWrongs,
      "numBurys" -> topic.meta.numBurys,
      "numUnwanteds" -> topic.meta.numUnwanteds,
      "numOrigPostLikes" -> topic.meta.numOrigPostLikeVotes,
      "numOrigPostReplies" -> topic.meta.numOrigPostRepliesVisible,
      "author" -> JsUserOrNull(topicStuff.author),
      "authorId" -> JsNumber(topic.meta.authorId),
      "authorUsername" -> JsStringOrNull(topicStuff.authorUsername),
      "authorFullName" -> JsStringOrNull(topicStuff.authorFullName),
      "authorAvatarUrl" -> JsStringOrNull(topicStuff.authorAvatarUrl),
      "createdEpoch" -> date(topic.meta.createdAt),
      "bumpedEpoch" -> dateOrNull(topic.meta.bumpedAt),
      "lastReplyEpoch" -> dateOrNull(topic.meta.lastReplyAt),
      "lastReplyer" -> JsUserOrNull(topicStuff.lastReplyer),
      "frequentPosters" -> JsArray(topicStuff.frequentPosters.map(JsUser)),
      "answeredAtMs" -> dateOrNull(topic.meta.answeredAt),
      "answerPostUniqueId" -> JsNumberOrNull(topic.meta.answerPostUniqueId),
      "plannedAtMs" -> dateOrNull(topic.meta.plannedAt),
      "doneAtMs" -> dateOrNull(topic.meta.doneAt),
      "closedAtMs" -> dateOrNull(topic.meta.closedAt),
      "lockedAtMs" -> dateOrNull(topic.meta.lockedAt),
      "frozenAtMs" -> dateOrNull(topic.meta.frozenAt))
  }

}

