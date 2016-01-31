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

/// <reference path="../../typedefs/react/react.d.ts" />
/// <reference path="../plain-old-javascript.d.ts" />

//------------------------------------------------------------------------------
   module debiki2.forum {
//------------------------------------------------------------------------------

var d = { i: debiki.internal, u: debiki.v0.util };
var r = React.DOM;
var reactCreateFactory = React['createFactory'];
var ReactBootstrap: any = window['ReactBootstrap'];
var Button = reactCreateFactory(ReactBootstrap.Button);
var ButtonGroup = reactCreateFactory(ReactBootstrap.ButtonGroup);
var Input = reactCreateFactory(ReactBootstrap.Input);
var ButtonInput = reactCreateFactory(ReactBootstrap.ButtonInput);
var Modal = reactCreateFactory(ReactBootstrap.Modal);
var ModalBody = reactCreateFactory(ReactBootstrap.ModalBody);
var ModalFooter = reactCreateFactory(ReactBootstrap.ModalFooter);
var ModalHeader = reactCreateFactory(ReactBootstrap.ModalHeader);
var ModalTitle = reactCreateFactory(ReactBootstrap.ModalTitle);

var editIntroDialog;

export function openEditIntroDialog() {
  if (!editIntroDialog) {
    editIntroDialog = ReactDOM.render(EditIntroDialog(), utils.makeMountNode());
  }
  editIntroDialog.open();
}


var EditIntroDialog = createClassAndFactory({
  getInitialState: function () {
    return { isOpen: false };
  },

  open: function(category: Category) {
    this.setState({ isOpen: true });
  },

  close: function() {
    this.setState({ isOpen: false });
  },

  editIntroText: function() {
    editor.openEditorToEditPost(BodyId);
    this.close();
  },

  removeIntroText: function() {
    ReactActions.setPostHidden(BodyId, true);
    this.close();
  },

  render: function () {
    var editIntroTextButton =
        r.div({ className: 'form-group' },
          Button({ onClick: this.editIntroText },
            r.span({ className: 'icon-edit' }, "Edit intro text")),
          r.div({ className: 'help-block' }, "Opens the editor."));

    var removeIntroButton =
        r.div({ className: 'form-group' },
          Button({ onClick: this.removeIntroText },
            r.span({ className: 'icon-cancel' }, "Remove intro")),
          r.div({ className: 'help-block' }, "Removes the intro text for everyone. " +
            "You can add it back again, by clicking the edit icon (",
              r.span({ className: 'icon-edit' }), ") to the right of the forum title."));

    var body =
        r.div({},
          r.p({}, "The forum intro text helps people understand the purpose of the forum. " +
              "They can hide it by clicking ",
              r.span({ className: 'icon-cancel' }, "Hide"), " when they have read it. " +
              " — Now, what do you want to do?"),
          editIntroTextButton,
          removeIntroButton);

    return (
      Modal({ show: this.state.isOpen, onHide: this.close, dialogClassName: 'esEditIntroDlg' },
        ModalHeader({}, ModalTitle({}, "Forum Intro Text")),
        ModalBody({}, body),
        ModalFooter({}, Button({ onClick: this.close }, "Cancel"))));
  }
});


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list
