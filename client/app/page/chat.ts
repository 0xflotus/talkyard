/*
 * Copyright (c) 2016 Kaj Magnus Lindberg
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
/// <reference path="../ReactStore.ts" />
/// <reference path="../react-elements/name-login-btns.ts" />
/// <reference path="../Server.ts" />
/// <reference path="../utils/utils.ts" />
/// <reference path="../utils/window-zoom-resize-mixin.ts" />
/// <reference path="../avatar/avatar.ts" />
/// <reference path="../avatar/AvatarAndName.ts" />
/// <reference path="../login/login.ts" />
/// <reference path="discussion.ts" />

//------------------------------------------------------------------------------
   module debiki2.page {
//------------------------------------------------------------------------------

var d = { i: debiki.internal, u: debiki.v0.util };
var r = React.DOM;
var reactCreateFactory = React['createFactory'];
var ReactBootstrap: any = window['ReactBootstrap'];
var Button = reactCreateFactory(ReactBootstrap.Button);
var DropdownButton = reactCreateFactory(ReactBootstrap.DropdownButton);
var MenuItem = reactCreateFactory(ReactBootstrap.MenuItem);

var EditorBecomeFixedDist = 5;
var DefaultEditorRows = 2;


export var ChatMessages = createComponent({
  componentDidUpdate: function() {
    // We should call onScroll() if a new message gets inserted below the current scroll pos.
    // Simply call it always, instead.
    this.refs.fixedAtBottom.onScroll();
  },

  scrollDown: function() {
    this.refs.titleAndMessages.scrollDown();
  },

  render: function() {
    var store: Store = this.props;
    var isChatMember = _.some(store.messageMembers, (m: BriefUser) => m.id === store.me.id);
    var editorOrJoinButton = isChatMember
        ? ChatMessageEditor({ store: this.props, scrollDownToViewNewMessage: this.scrollDown })
        : JoinChatButton({ store: this.props, isChatMember: isChatMember });
    return (
      r.div({ className: 'esChatPage dw-page' },
        TitleAndLastChatMessages({ store: this.props, ref: 'titleAndMessages' }),
        FixedAtBottom({ ref: 'fixedAtBottom' },
          editorOrJoinButton)));
  }
});



var TitleAndLastChatMessages = createComponent({
  getInitialState: function() {
    return {};
  },

  componentDidMount: function() {
    this.scrollDown();
    this.setState({ hasScrolledDown: true });
  },

  componentWillUpdate: function() {
    // Scroll down, if comment added, & we're at the bottom already.
    var pageColumnRect = getPageRect();
    // Add +2 because sometimes .bottom is 0.1 more than the-win-height, for some weird reason.
    this.shallScrollDown = pageColumnRect.bottom <= $(window).height() + 2;
  },

  componentDidUpdate: function() {
    if (this.shallScrollDown) {
      this.scrollDown();
    }
  },

  scrollDown: function() {
    var pageColumn = document.getElementById('esPageColumn');
    pageColumn.scrollTop = pageColumn.scrollHeight;
  },

  render: function () {
    var store: Store = this.props.store;
    var title = Title(store); // later: only if not scrolled down too far

    var originalPost = store.allPosts[store.rootPostId];
    var origPostAuthor = store.usersByIdBrief[originalPost.authorIdInt];
    var origPostHeader = PostHeader({ store: store, post: originalPost });
    var origPostBody = PostBody({ store: store, post: originalPost });

    var messages = [];
    _.each(store.allPosts, (post: Post) => {
      if (post.postId === TitleId || post.postId === BodyId) {
        // We show the title & body elsewhere.
        return;
      }
      messages.push(
        ChatMessage({ store: store, post: post }));
    });

    var thisIsTheWhat =
        r.p({},
          "This is the " + ReactStore.getPageTitle() + " chat channel, created by ",
          avatar.AvatarAndName({ user: origPostAuthor, hideAvatar: true }),
          ", ", timeExact(originalPost.createdAt));

    var perhapsHidden;
    if (!this.state.hasScrolledDown) {
      // Avoid flash of earlier messages before scrolling to end.
      perhapsHidden = { display: 'none' };
    }

    return (
      r.div({ className: 'esLastChatMsgs', style: perhapsHidden },
        title,
        r.div({ className: 'esChatChnl_about'},
          thisIsTheWhat,
          r.div({}, "Purpose:"),
          origPostBody),
        messages,
        r.div({ id: 'dw-the-end' })));
  }
});



var ChatMessage = createComponent({
  render: function () {
    var store: Store = this.props.store;
    var post: Post = this.props.post;
    var author: BriefUser = store.usersByIdBrief[post.authorId];
    return (
      r.div({ className: 'esChatMsg' },
        avatar.Avatar({ user: author }),
        PostHeader({ store: store, post: post, isFlat: true, exactTime: true }),
        PostBody({ store: store, post: post })));
  }
});



var FixedAtBottom = createComponent({
  mixins: [utils.PageScrollMixin, utils.WindowZoomResizeMixin],

  getInitialState: function() {
    return { fixed: false, bottom: 0 };
  },

  componentDidMount: function() {
    // Currently we always scroll to the bottom, when opening a chat channel.
    // Later: setState fixed: true, if going back to a chat channel when one has scrolled up.
  },

  onWindowZoomOrResize: function() {
    this.onScroll();
  },

  onScroll: function() {
    var pageBottom = getPageRect().bottom;
    var scrollableBottom = $(window).height();
    var myNewBottom = pageBottom - scrollableBottom;
    this.setState({ bottom: myNewBottom });
    if (!this.state.fixed) {
      if (pageBottom > scrollableBottom + EditorBecomeFixedDist) {
        this.setState({ fixed: true });
      }
    }
    else {
      // Add +X otherwise sometimes the fixed state won't vanish although back at top of page.
      if (pageBottom - scrollableBottom <= +2) {
        this.setState({ fixed: false, bottom: 0 });
      }
    }
  },

  render: function () {
    var offsetBottomStyle;
    if (this.state.fixed) {
      offsetBottomStyle = { bottom: this.state.bottom };
    }
    return (
      r.div({ className: 'esFixAtBottom', style: offsetBottomStyle },
        this.props.children));
  }
});



var JoinChatButton = createComponent({
  joinChannel: function() {
    login.loginIfNeededAndThen(LoginReason.LoginToChat, '#theJonChatBtn', () => {
      if (this.props.isChatMember) {
        // Now after having logged in, we know that this user is a channel member already.
        // Need do nothing.
      }
      else {
        Server.joinChatChannel();
      }
    });
  },

  render: function() {
    return (
      r.div({ className: 'esJoinChat' },
        Button({ id: 'theJonChatBtn', className: 'esJoinChat_btn',
            onClick: this.joinChannel, bsStyle: 'primary' },
          "Join this chat")));
  }
});



var ChatMessageEditor = createComponent({
  getInitialState: function() {
    return {
      text: '',
      editingPostId: null,
      editingPostUid: null,
      rows: DefaultEditorRows,
    };
  },

  onTextEdited: function(event) {
    // numLines won't work with wrapped lines, oh well, fix some other day.
    var numLines = event.target.value.split(/\r\n|\r|\n/).length;
    this.setState({
      text: event.target.value,
      rows: Math.max(DefaultEditorRows, Math.min(8, numLines)),
    });
  },

  onKeyPress: function(event) {
    if (event.charCode === 13 && !event.shiftKey && !event.ctrlKey) {
      // Enter or Return without Shift or Ctrl down means "post chat message".
      var isNotEmpty = /\S/.test(this.state.text);
      if (isNotEmpty) {
        this.postChatMessage();
      }
    }
  },

  postChatMessage: function() {
    this.setState({ isSaving: true });
    Server.insertChatMessage(this.state.text, () => {
      if (!this.isMounted()) return;
      this.setState({ text: '', isSaving: false, rows: DefaultEditorRows });
      this.refs.textarea.getDOMNode().focus();
      this.props.scrollDownToViewNewMessage();
    });
  },

  render: function () {
    return (
      r.div({ className: 'esChatMsgEdtr' },
        r.textarea({ className: 'esChatMsgEdtr_textarea', ref: 'textarea',
          value: this.state.text, onChange: this.onTextEdited,
          onKeyPress: this.onKeyPress,
          placeholder: "Type here. You can use Markdown and HTML.",
          disabled: this.state.isSaving,
          rows: this.state.rows })));
  }
});

// Staying at the bottom: http://blog.vjeux.com/2013/javascript/scroll-position-with-react.html

//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=tcqwn list
