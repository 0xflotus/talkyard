/**
 * Copyright (C) 2015 Kaj Magnus Lindberg
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

import com.debiki.core.Prelude._
import java.{util => ju}
import scala.collection.immutable


/** Means that something should be reviewed, e.g. a post or a user should be reviewed.
  *
  * @param causedById A user that did something possibly harmful and therefore what s/he did
  *   should be reviewed. Or noticed a bad post and flagged it, and in that way actively
  *   created a review task. Or the system user, when it sees a very unpopular post, and
  *   therefore creates a review task.
  * @param completedById The staff user that had a look at this review task and e.g. deleted
  *   a spam comment, or dismissed the review task if the comment was ok.
  * @param invalidatedAt If there is e.g. a review task about a comment, but the comment gets
  *   deleted, then the review task becomes invalid. Perhaps just delete the review task too? Hmm.
  *   StackExchange has an invalidated_at field.
  * @param userId A user who e.g. changed his/her avatar and his/her profile should therefore
  *   be reviewed.
  * @param pageId A new page that should be reviewed.
  * @param postId A post that should be reviewed, it might be spam for example.
  */
case class ReviewTask(
  id: ReviewTaskId,
  reasons: immutable.Seq[ReviewReason],
  causedById: UserId,
  createdAt: ju.Date,
  createdAtRevNr: Option[Int] = None,
  moreReasonsAt: Option[ju.Date] = None,
  completedAt: Option[ju.Date] = None,
  completedAtRevNr: Option[Int] = None,
  completedById: Option[UserId] = None,
  invalidatedAt: Option[ju.Date] = None,
  resolution: Option[Int] = None,
  userId: Option[UserId] = None,
  pageId: Option[PageId] = None,
  postId: Option[UniquePostId] = None,
  postNr: Option[PostId] = None) {

  require(reasons.nonEmpty, "EsE3FK21")
  require(!moreReasonsAt.exists(_.getTime < createdAt.getTime), "EsE7UGYP2")
  require(!completedAt.exists(_.getTime < createdAt.getTime), "EsE0YUL72")
  require(!invalidatedAt.exists(_.getTime < createdAt.getTime), "EsE5GKP2")
  require(completedAt.isEmpty || invalidatedAt.isEmpty, "EsE2FPW1")
  require(completedAt.isEmpty || resolution.isDefined, "EsE0YUM4")
  require(!completedAtRevNr.exists(_ < FirstRevisionNr), "EsE1WL43")
  // A review task is either about a user, or about a page/post, but not both:
  require(userId.isEmpty || postId.isEmpty, "EsE6GPVU4")
  require(userId.isEmpty || pageId.isEmpty, "EsE4JYU7")
  require(!postId.exists(_ <= 0), "EsE3GUL80")
  require(postId.isDefined == postNr.isDefined, "EsE6JUM13")
  require(postId.isDefined == createdAtRevNr.isDefined, "EsE5PUY0")
  require(postId.isEmpty || completedAt.isDefined == completedAtRevNr.isDefined, "EsE4PU2")


  def doneOrGone = completedAt.isDefined || invalidatedAt.isDefined


  def mergeWithAny(anyOldTask: Option[ReviewTask]): ReviewTask = {
    val oldTask = anyOldTask getOrElse {
      return this
    }
    require(oldTask.id == this.id, "EsE4GPMU0")
    require(oldTask.completedAt.isEmpty, "EsE4FYC2")
    require(oldTask.causedById == this.causedById, "EsE6GU20")
    require(oldTask.createdAt.getTime <= this.createdAt.getTime, "EsE7JGYM2")
    require(!oldTask.moreReasonsAt.exists(_.getTime > this.createdAt.getTime), "EsE2QUX4")
    require(oldTask.userId == this.userId, "EsE5JMU1")
    require(oldTask.postId == this.postId, "EsE2UYF7")
    // Cannot add more review reasons to an already completed task.
    require(oldTask.completedAt.isEmpty, "EsE1WQC3")
    require(oldTask.invalidatedAt.isEmpty, "EsE7UGMF2")
    val newReasonsValue = ReviewReason.toLong(oldTask.reasons) + ReviewReason.toLong(this.reasons)
    val newReasonsSeq = ReviewReason.fromLong(newReasonsValue)
    this.copy(
      reasons = newReasonsSeq,
      createdAt = oldTask.createdAt,
      moreReasonsAt = Some(this.createdAt))
  }

}



