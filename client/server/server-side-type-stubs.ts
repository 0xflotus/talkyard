// These dummy types prevent compilation errors when compiling TypeScript for
// server side rendering.
//
// The classes listed below aren't included among the server side TypeScript files,
// so unless they're declared here the TypeScript compiler complains.


declare var debiki: any;

declare module debiki2 {
    var ReactActions: any;
    var ReactStore: any;
    var StoreListenerMixin: any;
}

