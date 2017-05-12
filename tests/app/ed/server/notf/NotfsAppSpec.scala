/**
 * Copyright (c) 2017 Kaj Magnus Lindberg
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

package ed.server.notf

import com.debiki.core._
import debiki.dao._
import debiki.{Globals, TextAndHtml}
import java.{util => ju}


class NotfsAppSpec extends DaoAppSuite() {
  var dao: SiteDao = _

  var createForumResult: CreateForumResult = _
  var categoryId: CategoryId = _

  var owner: Member = _
  var ownerWho: Who = _
  var moderator: Member = _
  var member1: Member = _
  var member2: Member = _
  var member3: Member = _
  var member4: Member = _
  var member5NotInAnyChat: Member = _
  var member6NotInAnyChat: Member = _
  var member7NotInAnyChat: Member = _
  var memberNeverMentioned: Member = _

  var withRepliesTopicId: PageId = _

  var oldChatTopicId: PageId = _
  var chatTopicOneId: PageId = _
  var chatTopicTwoId: PageId = _
  var chatTopicManyJoinedId: PageId = _

  var expectedTotalNumMentions = 0


  def countTotalNumNotfs(): Int =
    dao.readOnlyTransaction(_.countNotificationsPerUser().values.sum)


  def listUsersNotifiedAbout(postId: PostId): Set[UserId] = {
    dao.readOnlyTransaction(_.listUsersNotifiedAboutPost(postId))
  }


  "NotificationGenerator can create and remove notifications" - {
    val now = new ju.Date()

    "prepare" in {
      Globals.test.setTime(startTime)
      Globals.systemDao.getOrCreateFirstSite()
      dao = Globals.siteDao(Site.FirstSiteId)
      owner = createPasswordOwner("ntf_ownr", dao)
      ownerWho = Who(owner.id, browserIdData)
      createForumResult = dao.createForum("Forum", "/notf-test-forum/", ownerWho)
      categoryId = createForumResult.defaultCategoryId
      moderator = createPasswordModerator("ntf_mod", dao)
      member1 = createPasswordUser("ntf_mem1", dao)
      member2 = createPasswordUser("ntf_mem2", dao)
      member3 = createPasswordUser("ntf_mem3", dao)
      member4 = createPasswordUser("ntf_mem4", dao)
      member5NotInAnyChat = createPasswordUser("ntf_0cht5", dao)
      member6NotInAnyChat = createPasswordUser("ntf_0cht6", dao)
      member7NotInAnyChat = createPasswordUser("ntf_0cht7", dao)
      memberNeverMentioned = createPasswordUser("ntf_wrng", dao)
    }

    "staff creates stuff" in {
      withRepliesTopicId = createPage(PageRole.Discussion,
        TextAndHtml.testTitle("withRepliesTopicId"), TextAndHtml.testBody("withRepliesTopicId bd"),
        owner.id, browserIdData, dao, Some(categoryId))
      reply(moderator.id, withRepliesTopicId, s"Reply 1 (post nr 2) by mod")(dao)
      expectedTotalNumMentions += 1

      // This stuff might be incorrectly matched & break the tests, if there're buggy SQL queries.
      oldChatTopicId = createPage(PageRole.OpenChat,
        TextAndHtml.testTitle("oldChatTopicId"), TextAndHtml.testBody("chat purpose 2953"),
        owner.id, browserIdData, dao, Some(categoryId))
      dao.addUsersToPage(Set(owner.id), oldChatTopicId, byWho = ownerWho)
      dao.addUsersToPage(Set(moderator.id), oldChatTopicId, byWho = ownerWho)
      chat(owner.id, oldChatTopicId, "chat message 1")(dao)
      // Needs to be a different member, otherwise the prev chat message gets appended to, instead.
      chat(moderator.id, oldChatTopicId, "chat message 2")(dao)

      chatTopicOneId = createPage(PageRole.OpenChat,
        TextAndHtml.testTitle("chatTopicId"), TextAndHtml.testBody("chatTopicId body"),
        owner.id, browserIdData, dao, Some(categoryId))

      chatTopicTwoId = createPage(PageRole.OpenChat,
        TextAndHtml.testTitle("chatTopicTwoId"),
        TextAndHtml.testBody("chatTopicTwoId purpose"),
        owner.id, browserIdData, dao, Some(categoryId))

      chatTopicManyJoinedId = createPage(PageRole.OpenChat,
        TextAndHtml.testTitle("chatTopicManyJoinedId"),
        TextAndHtml.testBody("chatTopicManyJoinedId purpose"),
        owner.id, browserIdData, dao, Some(categoryId))
      dao.addUsersToPage(Set(owner.id), chatTopicManyJoinedId, byWho = ownerWho)
      dao.addUsersToPage(Set(moderator.id), chatTopicManyJoinedId, byWho = ownerWho)
      dao.addUsersToPage(Set(member1.id), chatTopicManyJoinedId, byWho = ownerWho)
      dao.addUsersToPage(Set(member2.id), chatTopicManyJoinedId, byWho = ownerWho)
      dao.addUsersToPage(Set(member3.id), chatTopicManyJoinedId, byWho = ownerWho)
      dao.addUsersToPage(Set(member4.id), chatTopicManyJoinedId, byWho = ownerWho)

      countTotalNumNotfs() mustBe expectedTotalNumMentions
    }

    "create no mention (in a chat)" - {
      "totally no mention" in {
        chat(member1.id, chatTopicManyJoinedId, "Hello")(dao)
        countTotalNumNotfs() mustBe expectedTotalNumMentions
      }
      "when typing an email address" in {
        chat(member1.id, chatTopicManyJoinedId, "Hello someone@example.com")(dao)
        countTotalNumNotfs() mustBe expectedTotalNumMentions
      }
      "when typing two @: @@" in {
        chat(member1.id, chatTopicManyJoinedId, s"Hello @@${member4.theUsername}")(dao)
        countTotalNumNotfs() mustBe expectedTotalNumMentions
      }
      "when typing a non-existing username" in {
        chat(member1.id, chatTopicManyJoinedId, "Hello @does_not_exist")(dao)
        countTotalNumNotfs() mustBe expectedTotalNumMentions
      }
      "when mentioning oneself" in {
        val myself = member1
        chat(myself.id, chatTopicManyJoinedId, s"Hello @${myself.theUsername}")(dao)
        countTotalNumNotfs() mustBe expectedTotalNumMentions
      }
    }

    "create specific people mentions (in a chat)" - {
      "create one mention, edit-delete, edit-recreate one mention" in {
        // Chat as mem 2 (not mem 1), so won't append to prev chat message.

        val chatPost = chat(member2.id, chatTopicManyJoinedId,
            s"Hello @${member1.theUsername}")(dao)
        expectedTotalNumMentions += 1
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(member1.id)
      }

      "create three mentions, edit-append delete two, real-edit-recreate one" in {
        info("mention 3 people")
        // Chat as mem 3 (not mem 2), so won't append to prev chat message.

        val chatPost = chat(member3.id, chatTopicManyJoinedId,
            s"Hi @${member1.theUsername} @${member2.theUsername} @${moderator.theUsername}")(dao)
        expectedTotalNumMentions += 3
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(member1.id, member2.id, moderator.id)

        info("edit and delete two mentions")
        edit(chatPost, member3.id,
            s"Hi @${member1.theUsername}")(dao)
        expectedTotalNumMentions -= 2
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(member1.id)

        info("chat-append-recreate one mention")
        val sameChatPost = chat(member3.id, chatTopicManyJoinedId,  // appends to the same message
            s"Hi @${member1.theUsername} and @${moderator.theUsername} again")(dao)
        sameChatPost.id mustBe chatPost.id
        expectedTotalNumMentions += 1
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(member1.id, moderator.id)
      }

      "mention someone not in the chat, then append-add, then edit-add others not in the chat" in {
        // Chat as mem 1 (not mem 3), so won't append to prev chat message.

        info("mention someone")
        val chatPost = chat(member1.id, chatTopicManyJoinedId,
            s"Hi @${member5NotInAnyChat.theUsername}")(dao)
        expectedTotalNumMentions += 1
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(member5NotInAnyChat.id)

        info("append to message & mention someone else")
        val samePost = chat(member1.id, chatTopicManyJoinedId,
            s"Hi @${member5NotInAnyChat.theUsername} @${member6NotInAnyChat.theUsername}")(dao)
        samePost.id mustBe chatPost.id
        expectedTotalNumMentions += 1
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(member5NotInAnyChat.id, member6NotInAnyChat.id)

        info("edit message & mention a third")
        edit(chatPost, member1.id,
          s"""Hi @${member5NotInAnyChat.theUsername} @${member6NotInAnyChat.theUsername}
              @${member7NotInAnyChat.theUsername}""")(dao)
        expectedTotalNumMentions += 1
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(
            member5NotInAnyChat.id, member6NotInAnyChat.id, member7NotInAnyChat.id)
      }
    }

    "mention @all" - {
      "if isn't staff = has no effect" in {
        // Post as member 2 not member 1, so as not to append to prev message.

        info("not when posting new chat message")
        val chatPost = chat(member2.id, chatTopicManyJoinedId, s"Hi @all")(dao)
        expectedTotalNumMentions += 0
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set.empty

        edit(chatPost, member2.id, s"""Bye bye""")(dao)
        expectedTotalNumMentions -= 0
        countTotalNumNotfs() mustBe expectedTotalNumMentions

        info("not when post-appending")
        val samePost = chat(member2.id, chatTopicManyJoinedId, s"... More @all")(dao)
        samePost.id mustBe chatPost.id
        expectedTotalNumMentions += 0
        countTotalNumNotfs() mustBe expectedTotalNumMentions

        edit(chatPost, member2.id, s"""Bye bye again""")(dao)
        expectedTotalNumMentions -= 0
        countTotalNumNotfs() mustBe expectedTotalNumMentions

        info("not when editing")
        edit(chatPost, member2.id, s"""Hi @all again""")(dao)
        expectedTotalNumMentions == 0
        countTotalNumNotfs() mustBe expectedTotalNumMentions
      }

      "in an empty chat (no one but oneself), won't notify people not in the chat" in {
        dao.addUsersToPage(Set(moderator.id), chatTopicTwoId, byWho = ownerWho)
        val chatPost = chat(moderator.id, chatTopicTwoId,
            s"Hi @all")(dao)
        expectedTotalNumMentions += 0
        countTotalNumNotfs() mustBe expectedTotalNumMentions
      }

      "in a chat with one other member, and edit-remove, chat-append" in {
        dao.addUsersToPage(Set(moderator.id), chatTopicOneId, byWho = ownerWho)
        dao.addUsersToPage(Set(member1.id), chatTopicOneId, byWho = ownerWho)

        info("say '... @all ...'")
        val chatPost = chat(moderator.id, chatTopicOneId,
            s"Hi @all")(dao)
        expectedTotalNumMentions += 1
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(member1.id)

        info("edit-remove '@all'")
        edit(chatPost, moderator.id,
            s"""Hi not all""")(dao)
        expectedTotalNumMentions -= 1
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set.empty

        info("chat-append '@all' again")
        val samePost = chat(moderator.id, chatTopicOneId,
            s"Hi @all")(dao)
        samePost.id mustBe chatPost.id
        expectedTotalNumMentions += 1
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(member1.id)

        info("edit-remove '@all'")
        edit(chatPost, moderator.id,
          s"""Hi not not not""")(dao)
        expectedTotalNumMentions -= 1
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set.empty

        info("edit-add '@all' a third time")
        edit(chatPost, moderator.id,
          s"""Hi @all""")(dao)
        expectedTotalNumMentions += 1
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe  Set(member1.id)
      }

      "mention @all + mem 4,5,6, in large chat, then edit-rm @all and mem 5, edit-add mem 7" in {
        val chatPost = chat(owner.id, chatTopicManyJoinedId,
            s"""@all + @${member4.theUsername} + @${member5NotInAnyChat.theUsername} +
                @${member6NotInAnyChat.theUsername}""")(dao)
        expectedTotalNumMentions += 7  // 6 (incl mem 4) - oneself + mem-5-and-6 = 7
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(
            member1.id, member2.id, member3.id, member4.id, moderator.id,
            member5NotInAnyChat.id, member6NotInAnyChat.id)

        info("edit-remove '@all', keep 4,6")
        edit(chatPost, owner.id,
            s"Only: @${member4.theUsername} @${member6NotInAnyChat.theUsername}")(dao)
        expectedTotalNumMentions -= 5
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(member4.id, member6NotInAnyChat.id)

        info("edit-add-back '@all' + member 7")
        edit(chatPost, owner.id,
            s"@all + @${member6NotInAnyChat.theUsername} + @${member7NotInAnyChat.theUsername}")(dao)
        expectedTotalNumMentions += 5
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(
            member1.id, member2.id, member3.id, member4.id, moderator.id,
            member6NotInAnyChat.id, member7NotInAnyChat.id)
      }

      "mention @all, kick a member, then remove @all — also the former member gets unmentioned" in {
        // Post as moderator, so as not to append to owner's prev message.

        val chatPost = chat(moderator.id, chatTopicManyJoinedId, "Good morning @all.")(dao)
        expectedTotalNumMentions += 5  // 6 - oneself
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(
            member1.id, member2.id, member3.id, member4.id, owner.id)

        dao.removeUsersFromPage(Set(member4.id), chatTopicManyJoinedId, byWho = ownerWho)

        info("edit-remove '@all'")
        edit(chatPost, moderator.id, "No one.")(dao)
        expectedTotalNumMentions -= 5
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set.empty

        info("edit-add '@all' now won't notify the removed member")
        edit(chatPost, moderator.id, "Hi @all!")(dao)
        expectedTotalNumMentions += 4
        countTotalNumNotfs() mustBe expectedTotalNumMentions
        listUsersNotifiedAbout(chatPost.id) mustBe Set(
            member1.id, member2.id, member3.id, owner.id)  // not member4
      }

      "in a non-chat discussion" in {
        // then what?
      }
    }
  }
}
