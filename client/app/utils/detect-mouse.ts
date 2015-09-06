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

/// <reference path="../../typedefs/jquery/jquery.d.ts" />
/// <reference path="../plain-old-javascript.d.ts" />

//------------------------------------------------------------------------------
   module debiki2.utils {
//------------------------------------------------------------------------------

var d: any = { i: debiki.internal, u: debiki.v0.util };
var $: JQueryStatic = d.i.$;

var pendingCallbacks: (() => void)[] = [];
var touchEvents = 'touchstart, touchend, touchmove';
var mouseMoveEvent = 'mousemove';


export var isMouseDetected: boolean = undefined;


/**
 * Works like so: If a mouse event happens before any touch event, we'll
 * assume that a mouse is present and in use. If a touch event happens first, then
 * even if a mouse is present (we don't know) apparently it's not in use.
 *
 * Adds a '.mouse' class to the <html> tag if mouse detected.
 *
 * (Some devices, e.g. my laptop, have both a touch screen and a mouse.)
 *
 * Not sure if this will work on all touch devices. Works on my Fair Phone Android
 * anyway.
 */
export function startDetectingMouse() {
  $(document).on(touchEvents, onFirstTouch);
  $(document).on(mouseMoveEvent, onFirstMouseMove);
}


function stopDetectingMouse() {
  $(document).off(touchEvents, onFirstTouch);
  $(document).off(mouseMoveEvent, onFirstMouseMove);
}


/**
 * The callback is called if a mouse is detected, otherwise never.
 */
export function onMouseDetected(callback: () => void) {
  if (isMouseDetected) {
    callback();
  }
  else {
    pendingCallbacks.push(callback);
  }
}


function onFirstTouch() {
  console.log('Touch screen in use, assuming no mouse.');
  stopDetectingMouse();
  isMouseDetected = false;
}


function onFirstMouseMove() {
  console.log('Mouse detected. [DwM4KGEW0]');
  stopDetectingMouse();
  isMouseDetected = true;
  $('html').addClass('mouse');
  for (var i = 0; i < pendingCallbacks.length; ++i) {
    pendingCallbacks[i]();
  }
  return false;
}


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 list
