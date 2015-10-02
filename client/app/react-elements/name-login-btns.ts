/*
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

/// <reference path="../../typedefs/react/react.d.ts" />
/// <reference path="../login/login-dialog.ts" />
/// <reference path="../ReactStore.ts" />

//------------------------------------------------------------------------------
   module debiki2.reactelements {
//------------------------------------------------------------------------------

var d = { i: debiki.internal, u: debiki.v0.util };
var r = React.DOM;


export var NameLoginBtns = createComponent({
  mixins: [debiki2.StoreListenerMixin],

  getInitialState: function() {
    return debiki2.ReactStore.allData();
  },

  onChange: function() {
    if (!this.isMounted()) {
      // Don't know how this can happen, but it does inside the NonExistingPage component.
      return;
    }
    this.setState(debiki2.ReactStore.allData());
  },

  onLoginClick: function() {
    login.getLoginDialog().open(this.props.purpose || 'LoginToLogin');
  },

  onLogoutClick: function() {
    // COULD let ReactActions call Server instead.
    debiki2.Server.logout(debiki2.ReactActions.logout);
  },

  goToUserPage: function() {
    goToUserPage(this.state.user.userId);
  },

  render: function() {
    var user = this.state.user;
    var userNameElem = null;
    if (user.isLoggedIn) {
      userNameElem =
          r.span({ className: 'dw-u-info' },
              r.a({ className: 'dw-u-name', onClick: this.goToUserPage }, user.fullName));
    }

    var loginBtnElem = null;
    if (!user.isLoggedIn) {
      loginBtnElem =
          r.span({ className: 'dw-a-login', onClick: this.onLoginClick, id: this.props.id },
              this.props.title || 'Login');
    }

    var logoutBtnElem = null;
    if (user.isLoggedIn) {
      logoutBtnElem =
          r.span({ className: 'dw-a-logout', onClick: this.onLogoutClick, id: this.props.id },
              'Logout');
    }

    var elems =
      r.span({ className: 'dw-u-lgi-lgo' },
        userNameElem,
        loginBtnElem,
        logoutBtnElem);

    return elems;
  }
});


export function goToUserPage(userId) {
  // If using an <a> link, then, if already in the /-/users/ SPA, no rerendering
  // of React elements will be triggered (not sure why) so the contents of the
  // page won't change: it'll show details for one user, but the URL will be
  // for another (namely the currently logged in user). Workaround: update
  // window.location — this rerenders the React components.
  window.location.assign('/-/users/#/id/' + userId);
}


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 list
