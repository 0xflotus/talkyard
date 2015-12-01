/*
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

/// <reference path="../../typedefs/react/react.d.ts" />
/// <reference path="../plain-old-javascript.d.ts" />
/// <reference path="../utils/react-utils.ts" />
/// <reference path="../utils/fade-in-on-click.ts" />
/// <reference path="../ReactStore.ts" />
/// <reference path="../Server.ts" />

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
var ReactSelect; // lazy loaded

var DefaultPosition = 50;

var editCategoryDialog;

export function getEditCategoryDialog(success: (dialog) => void) {
  if (editCategoryDialog) {
    success(editCategoryDialog);
  }
  else {
    Server.loadEditorEtceteraScripts().done(() => {
      ReactSelect = reactCreateFactory(window['Select']); // react-select
      editCategoryDialog = React.render(EditCategoryDialog(), debiki2.utils.makeMountNode());
      success(editCategoryDialog);
    });
  }
}


var EditCategoryDialog = createClassAndFactory({
  getInitialState: function () {
    return {
      isOpen: false,
      newTopicTypes: [],
    };
  },

  open: function(categoryId?: number) {
    this.setState({
      categoryId: categoryId,
      isOpen: true,
      isLoading: !!categoryId,
      isSaving: false,
      isCreating: !categoryId,
      isEditing: !!categoryId,
    });
    if (categoryId) {
      Server.loadCategory(categoryId, (category: Category) => {
        this.setState({
          isLoading: false,
          name: category.name,
          slug: category.slug,
          newTopicTypes: category.newTopicTypes,
          position: category.position,
          hideInForum: category.hideInForum,
        });
      });
    }
    else {
      this.setState({
        name: '',
        slug: '',
        newTopicTypes: [PageRole.Discussion],
        position: DefaultPosition,
        hideInForum: false,
      });
    }
  },

  close: function() {
    this.setState({
      isOpen: false,
    });
  },

  onNameChanged: function(event) {
    var editedName = event.target.value;
    this.setState({ name: editedName });
    // If this is a new category, it's okay to change the slug. Otherwise, avoid changing it,
    // because it'd break external links to the category.
    if (this.state.isCreating) {
      var slugMatchingName = window['debikiSlugify'](editedName);
      this.setState({ slug: slugMatchingName });
    }
  },

  onSlugChanged: function(event) {
    this.setState({ slug: event.target.value });
  },

  onPositionChanged: function(event) {
    var newPosition = parseInt(event.target.value);
    this.setState({ position: isNaN(newPosition) ? '' : newPosition });
  },

  onTopicTypesChange: function(topicTypesText: string, selectedOptions) {
    var topicTypes = selectedOptions.map(x => x.value);
    this.setState({ newTopicTypes: topicTypes });
  },

  toggleHideInForum: function() {
    this.setState({ hideInForum: !this.state.hideInForum });
  },

  save: function() {
    this.setState({ isSaving: true });
    var category = {
      categoryId: this.state.categoryId,
      parentCategoryId: ReactStore.getCategoryId(),
      sectionPageId: debiki.internal.pageId,
      name: this.state.name,
      slug: this.state.slug,
      position: this.state.position || DefaultPosition,
      newTopicTypes: this.state.newTopicTypes,
      hideInForum: this.state.hideInForum,
    };
    ReactActions.saveCategory(category, this.close, () => {
      this.setState({ isSaving: false });
    });
  },

  render: function () {
    var nameInput =
        Input({ type: 'text', label: "Name", ref: 'nameInput',
            value: this.state.name, onChange: this.onNameChanged,
            help: "Keep it short, only one word, if possible." });

    var topicTypes = [   // [i18n]
        { value: PageRole.Question, label: 'Question' },
        { value: PageRole.Problem, label: 'Problem' },
        { value: PageRole.Idea, label: 'Idea' },
        { value: PageRole.Discussion, label: 'Discussion' },
        { value: PageRole.Critique, label: 'Critique' }]; // [plugin]

    var topicTypesInput =
      r.div({ className: 'form-group' },
        r.label({ className: 'control-label' }, "Topic types"),
        ReactSelect({ value: this.state.newTopicTypes.join(), options: topicTypes, multi: true,
            onChange: this.onTopicTypesChange, className: 'dw-topic-types' }),
        r.span({ className: 'help-block' },
          "The topic types to choose among, when creating a new topic. ",
          r.i({}, "Discussion"), " is the default. ",
          r.i({}, "Problem"), " is if something is broken or doesn't work, needs to be fixed."));

    var slugInput =
        utils.FadeInOnClick({ clickToShowText: "Click to change how the name looks in URLs" },
          Input({ type: 'text', label: "Slug",
              ref: 'slugInput', value: this.state.slug, onChange: this.onSlugChanged,
              help: "The slug is shown in the URL in the browser address bar." }));

    var sortPositionText = "Click to set sort position";
    if (this.state.position !== DefaultPosition) {
      sortPositionText += " (" + this.state.position + ")";
    }
    var positionInput =
        utils.FadeInOnClick({ clickToShowText: sortPositionText },
          Input({ type: 'number', label: "Position",
            value: this.state.position || '', onChange: this.onPositionChanged,
            help: "Categories with lower positions are listed first. Default: " +
                DefaultPosition }));

    var hideInForumTitle = "Hide in forum (" + (this.state.hideInForum ?  "yes)" : "no)");
    var hideInForumInput =
        utils.FadeInOnClick({ clickToShowText: hideInForumTitle },
            Input({ type: 'checkbox', label: "Hide in forum",
              checked: this.state.hideInForum, onChange: this.toggleHideInForum,
              help: "Hides this category and all topics herein, in the forum topic lists — " +
                  "only staff will see them. However, when accessed directly, the pages " +
                  "will be visible. This is useful for pages like a homepage or about-this-" +
                  "website page, which you might not want people to see in the forum. " +
                  "Default: false" }));

    var body = this.state.isLoading
        ? r.div({}, "Loading...")
        : r.div({},
            nameInput,
            topicTypesInput,
            slugInput,
            positionInput,
            hideInForumInput);

    var saveButtonTitle = this.state.isCreating ? "Create Category" : "Save Edits";
    var dialogTitle = this.state.isCreating ? saveButtonTitle : "Edit Category";

    var saveCancel = this.state.isSaving
        ? r.div({}, "Saving...")
        : r.div({},
            Button({ onClick: this.save }, saveButtonTitle),
            Button({ onClick: this.close }, "Cancel"));

    return (
      Modal({ show: this.state.isOpen, onHide: this.close,
          dialogClassName: 'dw-dlg-save-category' },
        ModalHeader({}, ModalTitle({}, dialogTitle)),
        ModalBody({}, body),
        ModalFooter({}, saveCancel)));
  }
});


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list
