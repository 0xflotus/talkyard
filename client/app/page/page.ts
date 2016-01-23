/*
 * Copyright (c) 2014-2016 Kaj Magnus Lindberg
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

/// <reference path="../../typedefs/react/react.d.ts" />
/// <reference path="../plain-old-javascript.d.ts" />
/// <reference path="../prelude.ts" />
/// <reference path="../utils/utils.ts" />
/// <reference path="../utils/react-utils.ts" />
/// <reference path="../dialogs.ts" />
/// <reference path="../help/help.ts" />
/// <reference path="../editor/title-editor.ts" />
/// <reference path="../edit-history/edit-history-dialog.ts" />
/// <reference path="../topbar/topbar.ts" />
/// <reference path="../page-dialogs/wikify-dialog.ts" />
/// <reference path="../page-dialogs/delete-post-dialog.ts" />
/// <reference path="../help/help.ts" />
/// <reference path="../model.ts" />
/// <reference path="discussion.ts" />
/// <reference path="chat.ts" />

// Wrapping in a module causes an ArrayIndexOutOfBoundsException: null error, see:
//  http://stackoverflow.com/questions/26189940/java-8-nashorn-arrayindexoutofboundsexception
// The bug has supposedly been fixed in Java 8u40. Once I'm using that version,
// remove `var exports = {};` from app/debiki/ReactRenderer.scala.
//------------------------------------------------------------------------------
  // module debiki2.renderer {
module boo {
    export var buu = 'vovvar';
};
//------------------------------------------------------------------------------

var MaxGuestId = -2; // place where?


var React = window['React']; // TypeScript file doesn't work
var r = React.DOM;
var ReactRouter = window['ReactRouter'];
var reactCreateFactory = React['createFactory'];
var ReactBootstrap: any = window['ReactBootstrap'];


function isServerSide() {
  // Don't change this to a static variable, because it'd be initialized rather late,
  // so some code would believe we were running client side.
  return !!window['java'];
}


var PageWithState = createComponent({
  mixins: [debiki2.StoreListenerMixin],

  getInitialState: function() {
    return debiki2.ReactStore.allData();
  },

  onChange: function() {
    this.setState(debiki2.ReactStore.allData());
  },

  render: function() {
    return Page(this.state);
  }
});


var Page = createComponent({
  render: function() {
    var store: Store = this.props;
    var content = page_isChatChannel(store.pageRole) ?
        debiki2.page.ChatMessages(this.props) : debiki2.page.TitleBodyComments(this.props);
    return (
      r.div({ className: 'esPage' },
        r.div({ className: 'container' },
          r.article({},
            content))));
  }
});


function renderTitleBodyComments() {
  var root = document.getElementById('dwPosts');
  if (!root)
    return;

  debiki2.avatar.resetAvatars();

  var store: Store = debiki2.ReactStore.allData();
  if (store.pageRole === PageRole.Forum) {
    var router = ReactRouter.create({
      routes: debiki2.forum.buildForumRoutes(),
      scrollBehavior: debiki2.forum.ForumScrollBehavior,
    });
    router.run(function(handler) {
      React.render(React.createElement(handler, store), root);
    });
  }
  else {
    React.render(PageWithState(), root);
  }
}


function renderTitleBodyCommentsToString() {
  debiki2.avatar.resetAvatars();

  // Comment in the next line to skip React server side and debug in browser only.
  //return '<p class="dw-page" data-reactid=".123" data-react-checksum="123">react_skipped</p>'
  var store: Store = debiki2.ReactStore.allData();
  if (store.pageRole === PageRole.Forum) {
    var routes = debiki2.forum.buildForumRoutes();
    var result;
    // In the future, when using the HTML5 history API to update the URL when navigating
    // inside the forum, we can use `store.pagePath` below. But for now, when using
    // the hash fragment, start at #/latest/ (the default route) always:
    var pagePath = '/latest/';
    ReactRouter.run(routes, pagePath, function(handler) {
      result = React.renderToString(React.createElement(handler, store));
    });
    return result;
  }
  else {
    return React.renderToString(Page(store));
  }
}

//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 list
