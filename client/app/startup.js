/* Bootstraps Debiki's browser stuff.
 * Copyright (C) 2010-2013 Kaj Magnus Lindberg (born 1979)
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

"use strict";

var d = { i: debiki.internal, u: debiki.v0.util };

debiki.window = $(window);
debiki.scriptLoad = $.Deferred();
debiki.FirstSiteId = '1';
debiki.debug = window.location.search.indexOf('debug=true') >= 0;
d.i.TitleId = 0;
d.i.BodyId = 1;


// Debiki convention: Dialog elem tabindexes should vary from 101 to 109.
// HTML generation code assumes this, too. See Debiki for Developers, #7bZG31.
d.i.DEBIKI_TABINDEX_DIALOG_MAX = 109;


// Tell KeyMaster to handle Escape clicks also inside <input>s.
keymaster.filter = function(event) {
  if (event.keyCode === 27) // escape is 27
    return true;
  var tagName = (event.target || event.originalTarget).tagName;
  return !(tagName == 'INPUT' || tagName == 'SELECT' || tagName == 'TEXTAREA');
};


function fireLoginOrLogout() {
  if (debiki2.ReactStore.getUser().isLoggedIn) {
    d.i.refreshFormXsrfTokens();
  }
};


function handleLoginInOtherBrowserTab() {
  var currentUser = debiki2.ReactStore.getUser();
  var sessionId = $.cookie('dwCoSid');
  if (currentUser.isLoggedIn) {
    if (sessionId) {
      // Session id example: (parts: hash, user id, name, login time, random value)
      // 'Y1pBlH7vY4JW9A.11.Magnus.1316266102779.15gl0p4xf7'
      var parts = sessionId.split('.');
      var newUserIdString = parts[1];
      if (currentUser.userId !== parseInt(newUserIdString)) {
        // We've logged in as another user in another browser tab.
        debiki2.ReactActions.login();
      }
    }
    else {
      // We've logged out in another browser tab.
      debiki2.ReactActions.logout();
    }
  }
  else if (sessionId) {
    // We've logged in in another browser tab.
    debiki2.ReactActions.login();
  }
}


function registerEventHandlersFireLoginOut() {
  fireLoginOrLogout();

  // If the user switches browser tab, s/he might logout and login
  // in another tab. That'd invalidate all xsrf tokens on this page,
  // and user specific permissions and ratings info (for this tab).
  // Therefore, when the user switches back to this tab, check
  // if a new session has been started.
  $(window).on('focus', handleLoginInOtherBrowserTab);

  //{{{ What will work w/ IE?
  // See http://stackoverflow.com/a/5556858/694469
  // But: "This script breaks down in IE(8) when you have a textarea on the
  // page.  When you click on the textarea, the document and window both
  // lose focus"
  //// IE EVENTS
  //$(document).bind('focusin', function(){
  //    alert('document focusin');
  //});
  //if (/*@cc_on!@*/false) { // check for Internet Explorer
  //  document.onfocusin = onFocus;
  //  document.onfocusout = onBlur;
  //} else {
  //  window.onfocus = onFocus;
  //  window.onblur = onBlur;
  //}
  //
  // http://stackoverflow.com/a/6184276/694469
  //window.addEventListener('focus', function() {
  //  document.title = 'focused';
  //});
  //window.addEventListener('blur', function() {
  //    document.title = 'not focused';
  //});
  //}}}
};


/**
 * XSRF token refresh, and JSON vulnerability protection
 * ((Details: Strips a certain reply prefix. This prevents the JSON
 * from being parsed as Javascript from a <script> tag. This'd otherwise
 * allow third party websites to turn your JSON resource URL into JSONP
 * request under some conditions, see:
 *   http://docs.angularjs.org/api/ng.$http, the "JSON Vulnerability
 * Protection" section, and:
 *   http://haacked.com/archive/2008/11/20/anatomy-of-a-subtle-json-vulnerability.aspx/ ))
 */
function configureAjaxRequests() {
  $.ajaxSetup({
    // There're questions at StackOverflow asking why `cache: false`
    // doesn't work with IE8. Does it not? I've not yet tested.
    cache: false,
    dataFilter: function (response, type) {
      // Don't know why, but `type` is alwyas undefined, so won't work:
      // if (type !== 'json') return response;
      // Sometimes, in FF (at least) and when the server replies 200 OK
      // with no body it seems, `response` is the `document` itself,
      // oddly enough, not a string.
      if (typeof response === 'string')
        response = response.replace(/^\)\]}',\n/, '');
      return response;
    },
    complete: function() {
      // Refresh <form> xsrf tokens, in case the server set a new cookie.
      // (That happens if the user logs in, or if I change certain server
      // side XSRF code, or perhaps I'll some day decide that XSRF tokens
      /// will be valid for one month only.)
      d.i.refreshFormXsrfTokens();
    }
  });
};



d.i.refreshFormXsrfTokens = function() {
  var token = $.cookie('XSRF-TOKEN');
  $('input.dw-fi-xsrf').attr('value', token);
};



/**
 * Renders the page, step by step, to reduce page loading time. (When the
 * first step is done, the user should conceive the page as mostly loaded.)
 */
function renderDiscussionPage() {

  configureAjaxRequests();

  var $posts = $('.debiki .dw-p:not(.dw-p-ttl)');

  (d.u.workAroundAndroidZoomBug || function() {})($);

  d.i.showCurLocationInSiteNav();

  // Do this before rendering the page.
  d.i.layout = d.i.chooseLayout();
  d.i.layoutThreads();

  // Make it possible to test React.js performance in the browser.
  if (location.search.indexOf('breakReactChecksums=true') !== -1) {
    $('[data-react-checksum]').attr('data-react-checksum', 'wrong-checksum-DwM4FKW21');
    console.log("I've altered the React.js checksums, everything will be rerendered. [DwM4KPW2]");
  }

  var timeBefore = performance.now();
  //debiki2.renderer.renderTitleBodyComments();

  renderTitleBodyComments();
  var timeAfterBodyComments = performance.now();

  debiki2.ReactStore.initialize();
  debiki2.startEarlyReactRoots();
  debiki2.ReactStore.activateUserSpecificData();
  var timeAfterUserData = performance.now();

  debiki2.startRemainingReactRoots();
  var timeAfterRemainingRoots = performance.now();

  console.log("Millis to render page: " + (timeAfterBodyComments - timeBefore) +
    ", to activate user data: " + (timeAfterUserData - timeAfterBodyComments) +
    ", for remaining React roots: " + (timeAfterRemainingRoots - timeAfterUserData) + " [DwM2FKQ1]");
  if (debiki.currentVersion === debiki.cachedVersion) {
    console.log("Page version: " + debiki.cachedVersion + " [DwM5KPJ9]");
  }
  else {
    console.log("Using old cached html version: [" + debiki.cachedVersion +
        "], current: [" + debiki.currentVersion +
        "], React.js should have logged a 'checksum was invalid' warning (in dev builds) [DwM4KGE8]");
  }

  $('html').addClass('dw-react-started');


  var steps = [];

  steps.push(function() {
    if (d.i.layout === 'TreeLayout') {
      debiki2.utils.onMouseDetected(d.i.initUtterscrollAndTips);
    }
    registerEventHandlersFireLoginOut();
    debiki2.utils.startDetectingMouse();
  });

  steps.push(function() {
    $posts.each(function() {
      d.i.makeThreadResizableForPost(this);
    });
  });

  steps.push(function() {
    debiki2.ReactActions.loadAndScrollToAnyUrlAnchorPost();
  });

  // Disable for now, I'll rewrite it to consider timestamps.
  //steps.push(d.i.startNextUnreadPostCycler);

  /* Post pins disabled right now, after I ported to React.
  steps.push(function() {
    d.i.makePinsDragsortable();
  }); */

  steps.push(function() {
    debiki.scriptLoad.resolve();
    // Disable for now, because it's a bit slow, and I don't save this server side anyway now.
    //debiki2.sidebar.UnreadCommentsTracker.start();
  });

  function runNextStep() {
    steps[0]();
    steps.shift();
    if (steps.length > 0)
      setTimeout(runNextStep, 70);
  }

  setTimeout(runNextStep, 60);
};


/**
 * Use this function if there is no root post on the page, but only meta info.
 * (Otherwise, if you use `renderDiscussionPage()`, some error happens, which kills
 * other Javascript that runs on page load.)
 */
d.i.renderEmptyPage = function() {
  // (Don't skip all steps, although the page is empty. For example, the admin
  // dashbar depends on login/logout events, and it's shown even if there's no
  // root post — e.g. on blog list pages, which list child pages only but no
  // main title or article.)
  configureAjaxRequests();
  debiki2.utils.onMouseDetected(d.i.initUtterscrollAndTips);
  debiki2.ReactStore.initialize();
  debiki2.startEarlyReactRoots();
  debiki2.startRemainingReactRoots();
  debiki2.ReactStore.activateUserSpecificData();
  fireLoginOrLogout();
  debiki2.utils.startDetectingMouse();
};


d.i.startDiscussionPage = function() {
  $(function() {
    if ($('.dw-page').length) {
      renderDiscussionPage();
    }
    else {
      // Skip most of the rendering step, since there is no Debiki page present.
      d.i.renderEmptyPage();
    }
  });
};


d.i.startEmbeddedEditor = function() {
  configureAjaxRequests();
  debiki2.editor.createEditor();
};


// Replace gifs with static images that won't play until clicked.
Gifffer();

// Show large images on click.
StupidLightbox.start('.dw-p-bd', ':not(.giffferated)');


// vim: fdm=marker et ts=2 sw=2 fo=tcqwn list
