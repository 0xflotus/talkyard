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
/// <reference path="../editor/PageRoleDropdown.ts" />
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
var PageRoleDropdown = editor.PageRoleDropdown;

var DefaultPosition = 50; // also in Scala [7KBYW2]

var editCategoryDialog;

export function getEditCategoryDialog(success: (dialog) => void) {
  if (editCategoryDialog) {
    success(editCategoryDialog);
  }
  else {
    Server.loadEditorEtcScriptsAndLater(() => {
      ReactSelect = reactCreateFactory(window['Select']); // react-select
      editCategoryDialog = ReactDOM.render(EditCategoryDialog(), debiki2.utils.makeMountNode());
      success(editCategoryDialog);
    });
  }
}


// BEM base name: esCatDlg
var EditCategoryDialog = createClassAndFactory({
  getInitialState: function () {
    return {
      isOpen: false,
      store: ReactStore.allData(),
      defaultTopicType: PageRole.Discussion,
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
          defaultTopicType: category.defaultTopicType,
          canChangeDefault: !category.isDefaultCategory,
          isDefault: category.isDefaultCategory,
          position: category.position,
          unlisted: category.unlisted,
          staffOnly: category.staffOnly,
          onlyStaffMayCreateTopics: category.onlyStaffMayCreateTopics,
        });
      });
    }
    else {
      this.setState({
        name: '',
        slug: '',
        defaultTopicType: PageRole.Discussion,
        canChangeDefault: true,
        isDefault: false,
        position: DefaultPosition,
        unlisted: false,
        staffOnly: false,
        onlyStaffMayCreateTopics: false,
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

  setDefaultTopicType: function(topicType: PageRole) {
    this.setState({ defaultTopicType: topicType });
  },

  onIsDefaultChanged: function(event) {
    this.setState({ isDefault: event.target.checked });
  },

  onPositionChanged: function(event) {
    var newPosition = parseInt(event.target.value);
    this.setState({ position: isNaN(newPosition) ? '' : newPosition });
  },

  toggleUnlisted: function() {
    this.setState({ unlisted: !this.state.unlisted });
  },

  toggleStaffOnly: function() {
    this.setState({ staffOnly: !this.state.staffOnly });
  },

  toggleOnlyStaffMayCreateTopics: function() {
    this.setState({ onlyStaffMayCreateTopics: !this.state.onlyStaffMayCreateTopics });
  },

  save: function() {
    this.setState({ isSaving: true });
    var category = {
      categoryId: this.state.categoryId,
      parentCategoryId: ReactStore.getCategoryId(),
      sectionPageId: debiki.internal.pageId,
      name: this.state.name,
      slug: this.state.slug,
      isDefault: this.state.isDefault,
      position: this.state.position || DefaultPosition,
      defaultTopicType: this.state.defaultTopicType,
      unlisted: this.state.unlisted,
      staffOnly: this.state.staffOnly,
      onlyStaffMayCreateTopics: this.state.onlyStaffMayCreateTopics,
    };
    ReactActions.saveCategory(category, this.close, () => {
      this.setState({ isSaving: false });
    });
  },

  render: function () {
    var store: Store = this.state.store;

    var nameInput =
        Input({ type: 'text', label: "Name", ref: 'nameInput',
            value: this.state.name, onChange: this.onNameChanged,
            help: "Keep it short, only one word, if possible." });

    var editDescriptionLink =
      r.div({ className: 'form-group' },
        r.label({ className: 'control-label' }, "Description"),
        r.div({},
          r.a({ href: linkToRedirToAboutCategoryPage(this.state.categoryId), target: '_blank' },
            "Edit description ", r.span({ className: 'icon-link-ext' }))),
        r.span({ className: 'help-block' },
          "Opens the category description page. Edit the first paragraph on that page."));

    var topicTypes = [   // [i18n]
        { value: PageRole.Question, label: 'Question' },
        { value: PageRole.Problem, label: 'Problem' },
        { value: PageRole.Idea, label: 'Idea' },
        { value: PageRole.Discussion, label: 'Discussion' }];
    if (debiki.siteId === '85') {
      topicTypes.push({ value: PageRole.Critique, label: 'Critique' }); // [plugin]
    }

    var defaultTopicTypeInput =
      r.div({ className: 'form-group' },
        r.label({ className: 'control-label' }, "Default topic type"),
        PageRoleDropdown({ store: store, pageRole: this.state.defaultTopicType,
          complicated: false, hideMore: true, onSelect: this.setDefaultTopicType,
          title: 'Topic type', className: 'esEdtr_titleEtc_pageRole', pullLeft: true }),
        r.span({ className: 'help-block' },
          "New topics in this category will be of this type, by default."));

    var isDefaultInput =
      Input({ type: 'checkbox', label: "Set as default category",
        checked: this.state.isDefault, onChange: this.onIsDefaultChanged,
        disabled: !this.state.canChangeDefault,
        help: "Places new topics in this category, if no other category selected." });

    var slugInput =
        utils.FadeInOnClick({ clickToShowText: "Click to change how the name looks in URLs" },
          Input({ type: 'text', label: "URL slug",
              ref: 'slugInput', value: this.state.slug, onChange: this.onSlugChanged,
              help: r.div({ className: 'esCatDlg_slug_help' },
                "Included in the computer address (URL) to this category. The address " +
                "would be: ",
                r.samp({}, location.origin + store.pagePath.value + RoutePathLatest + '/',
                  r.span({ className: 'esCatDlg_slug_help_addr_slug' }, this.state.slug))) }));

    var sortPositionText = "Click to set sort position";
    if (this.state.position !== DefaultPosition) {
      sortPositionText += " (" + this.state.position + ")";
    }
    var positionInput =
        utils.FadeInOnClick({ clickToShowText: sortPositionText },
          Input({ type: 'number', label: "Position",
            value: this.state.position || '', onChange: this.onPositionChanged,
            help: "On the category list page, categories with lower values are listed first. " +
              "Default: " + DefaultPosition }));

    var unlistedTitle = "Unlisted (" + (this.state.unlisted ?  "yes)" : "no)");
    var unlistedInput =
        utils.FadeInOnClick({ clickToShowText: unlistedTitle },
            Input({ type: 'checkbox', label: "Unlisted",
              checked: this.state.unlisted, onChange: this.toggleUnlisted,
              help: "Hides this category and all topics herein, in the forum topic lists — " +
                  "only staff will see them. However, when accessed directly, the pages " +
                  "will be visible. This is useful for pages like a homepage or about-this-" +
                  "website page, which people shouldn't see in the forum topic list." }));

    var staffOnlyTitle = "Staff only (" + (this.state.staffOnly ?  "yes)" : "no)");
    var staffOnlyInput =
      utils.FadeInOnClick({ clickToShowText: staffOnlyTitle },
        Input({ type: 'checkbox', label: "Staff only",
          checked: this.state.staffOnly, onChange: this.toggleStaffOnly,
          help: "Shall topics in this category be accessible only to admins and moderators?" }));

    var onlyStaffMayCreateTopicsTitle = "Only staff may create topics (" +
          (this.state.onlyStaffMayCreateTopics ?  "yes)" : "no)");
    var onlyStaffMayCreateTopicsInput =
      utils.FadeInOnClick({ clickToShowText: onlyStaffMayCreateTopicsTitle },
        Input({ type: 'checkbox', label: "Only staff may create topics",
          checked: this.state.onlyStaffMayCreateTopics, onChange: this.toggleOnlyStaffMayCreateTopics,
          help: "May only admins and moderators create topics in this category?" }));

    var body = this.state.isLoading
        ? r.div({}, "Loading...")
        : r.div({},
            nameInput,
            editDescriptionLink,
            defaultTopicTypeInput,
            isDefaultInput,
            slugInput,
            positionInput,
            unlistedInput,
            staffOnlyInput,
            onlyStaffMayCreateTopicsInput);

    var saveButtonTitle = this.state.isCreating ? "Create Category" : "Save Edits";
    var dialogTitle = this.state.isCreating ? saveButtonTitle : "Edit Category";

    var saveCancel = this.state.isSaving
        ? r.div({}, "Saving...")
        : r.div({},
            Button({ onClick: this.save, bsStyle: 'primary' }, saveButtonTitle),
            Button({ onClick: this.close }, "Cancel"));

    return (
      Modal({ show: this.state.isOpen, onHide: this.close,
          dialogClassName: 'esCatDlg' },
        ModalHeader({}, ModalTitle({}, dialogTitle)),
        ModalBody({}, body),
        ModalFooter({}, saveCancel)));
  }
});


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list
