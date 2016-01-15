/*
 * Copyright (c) 2015 Kaj Magnus Lindberg
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

// In this file: Small functions that says something about a model class instance.
// Would have been member functions, had it been possible to amend the React
// state tree with functions.


// Tells if a user may do something, and why s/he may do that, or why not.
interface MayMayNot {
  value: boolean;
  do_: boolean;   // true = may do it, use like so: if (may.do_) ...
  not: boolean;   // true = may not, use like so:   if (may.not) ...
  yes: boolean;   // true = may do it  -- try to remove?
  no: boolean;    // true = may not    -- try to remove?
  reason?: string;
}

function mayMayNot(may: boolean, reason: string): MayMayNot {
  return { value: may, do_: may, not: !may, yes: may, no: !may, reason: reason };
}

function mayIndeed() {
  return mayMayNot(true, null);
}


function hasChatSection(pageRole: PageRole) {
  // On message pages, replies are flat already, so an additional flat section makes no sense.
  return pageRole !== PageRole.Message;
}

function canClose(pageRole: PageRole) {
  // Lock messages instead so no new replies can be added.
  return pageRole !== PageRole.Message;
}

function page_isChat(pageRole: PageRole): boolean {
  return pageRole === PageRole.OpenChat || pageRole === PageRole.PrivateChat;
}

function page_isDiscussion(pageRole: PageRole): boolean {
  return pageRole && !isSection(pageRole) &&
      pageRole !== PageRole.SpecialContent &&
      pageRole !== PageRole.HomePage;
}

function isPageWithComments(pageRole: PageRole): boolean {
  return page_isDiscussion(pageRole) && pageRole !== PageRole.Message;
}

function isSection(pageRole: PageRole): boolean {
  return pageRole === PageRole.Forum || pageRole === PageRole.Blog;
}

function isPageWithSidebar(pageRole: PageRole): boolean {
  return true; // hmm remove this fn then, now
}

function pageRole_shallListInRecentTopics(pageRole: PageRole): boolean {
  switch (pageRole) {
    case PageRole.Message: // shown in the Direct Messages watchbar section instead
    case PageRole.EmbeddedComments:
    case PageRole.Blog:
    case PageRole.Forum:
    case PageRole.HomePage:
    case PageRole.Code:
    case PageRole.SpecialContent:
      return false;
    default:
      return !!pageRole;
  }
}


function me_isStranger(me: Myself): boolean {
  return !me.id;
}


function userGetWatchbarTopicIds(user: Myself): PageId[] {
  var watchbarTopics: WatchbarTopics = user.watchbarTopics;
  if (!watchbarTopics) return [];
  // For now: Concat with something so as to not return the original array.
  return watchbarTopics.recentTopics.map(t => t.pageId).concat([]);
}


function maySendInvites(user: Myself | CompleteUser): MayMayNot {
  // Currently only admins may send invites.
  if (!user.isAdmin) return mayMayNot(false, "is not admin");
  return mayIndeed();
}


function user_isMember(user: CompleteUser | BriefUser): boolean {
  return user.id > MaxGuestId;
}

function isGuest(user) {  // later: rename to user_isGuest
  // (Should rename userId to id.)
  return user.id <= MaxGuestId ||  // if is a CompleteUser
      user.userId <= MaxGuestId; // in case it's a User or BriefUser
}

function isMember(user: Myself | CompleteUser): boolean {
  if (!user) return false;
  var id = user['id'] || user['userId'];
  var member = id >= MinMemberId;
  //dieIf(isGuest(user) && member, 'EsE7YKU2');
  return member;
}

function isStaff(user: Myself) {
  return user.isAdmin || user.isModerator;
}


function isTalkToMeNotification(notf: Notification): boolean {
  return notf.type === NotificationType.DirectReply ||
          notf.type === NotificationType.Mention ||
          notf.type === NotificationType.Message;
}

function isTalkToOthersNotification(notf: Notification): boolean {
  return notf.type === NotificationType.NewPost;
}



function isCollapsed(post) {
  return post.isTreeCollapsed || post.isPostCollapsed;
}


function isDeleted(post) {
  return !post || post.isTreeDeleted || post.isPostDeleted;
}


function isWikiPost(postOrPostType: any) {
  var type;
  if (postOrPostType) {
    type = postOrPostType.postType || postOrPostType;
  }
  return type === PostType.StaffWiki || type === PostType.CommunityWiki;
}


// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list
