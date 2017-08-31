/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui.dirlist;

import static com.android.documentsui.testing.TestEvents.Mouse.CLICK;
import static com.android.documentsui.testing.TestEvents.Mouse.SECONDARY_CLICK;
import static com.android.documentsui.testing.TestEvents.Mouse.SHIFT_CLICK;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.MotionEvent;

import com.android.documentsui.selection.SelectionHelper;
import com.android.documentsui.selection.SelectionProbe;
import com.android.documentsui.testing.SelectionHelpers;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEventDetailsLookup;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestPredicate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * UserInputHandler / MultiSelectManager integration test covering the shared
 * responsibility of range selection.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public final class UserInputHandler_RangeTest {

    private static final List<String> ITEMS = TestData.create(100);

    private UserInputHandler mInputHandler;
    private TestActionHandler mActionHandler;

    private SelectionProbe mSelection;
    private TestFocusHandler mFocusHandler;
    private TestEventDetailsLookup mDetailsLookup;
    private TestPredicate<DocumentDetails> mCanSelect;
    private TestEventHandler<MotionEvent> mRightClickHandler;
    private TestEventHandler<MotionEvent> mDragAndDropHandler;
    private TestEventHandler<MotionEvent> mGestureSelectHandler;
    private TestEventHandler<Void> mPerformHapticFeedback;

    @Before
    public void setUp() {

        SelectionHelper selectionMgr = SelectionHelpers.createTestInstance(ITEMS);
        mActionHandler = new TestActionHandler();
        mDetailsLookup = new TestEventDetailsLookup();
        mFocusHandler = new TestFocusHandler();
        mSelection = new SelectionProbe(selectionMgr);
        mCanSelect = new TestPredicate<>();
        mRightClickHandler = new TestEventHandler<>();
        mDragAndDropHandler = new TestEventHandler<>();
        mGestureSelectHandler = new TestEventHandler<>();

        mInputHandler = new UserInputHandler(
                mActionHandler,
                mFocusHandler,
                selectionMgr,
                mDetailsLookup,
                mCanSelect,
                mRightClickHandler::accept,
                mDragAndDropHandler::accept,
                mGestureSelectHandler::accept,
                () -> mPerformHapticFeedback.accept(null));
    }

    @Test
    public void testExtendRange() {
        // uni-click just focuses.
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mSelection.assertRangeSelection(7, 11);
    }

    @Test
    public void testExtendRangeContinues() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mDetailsLookup.initAt(21);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mSelection.assertRangeSelection(7, 21);
    }

    @Test
    public void testMultipleContiguousRanges() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        // click without shift sets a new range start point.
        mDetailsLookup.initAt(20);
        mInputHandler.onSingleTapUp(CLICK);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mFocusHandler.focusPos = 20;

        mDetailsLookup.initAt(25);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);
        mInputHandler.onSingleTapConfirmed(SHIFT_CLICK);

        mSelection.assertRangeNotSelected(7, 11);
        mSelection.assertRangeSelected(20, 25);
        mSelection.assertSelectionSize(6);
    }

    @Test
    public void testReducesSelectionRange() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(17);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mDetailsLookup.initAt(10);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mSelection.assertRangeSelection(7, 10);
    }

    @Test
    public void testReducesSelectionRange_Reverse() {
        mDetailsLookup.initAt(17).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(7);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mDetailsLookup.initAt(14);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mSelection.assertRangeSelection(14, 17);
    }

    @Test
    public void testExtendsRange_Reverse() {
        mDetailsLookup.initAt(12).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(5);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mSelection.assertRangeSelection(5, 12);
    }

    @Test
    public void testExtendsRange_ReversesAfterForwardClick() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mDetailsLookup.initAt(0);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mSelection.assertRangeSelection(0, 7);
    }

    @Test
    public void testRightClickEstablishesRange() {

        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onDown(SECONDARY_CLICK);
        // This next method call simulates the behavior of the system event dispatch code.
        // UserInputHandler depends on a specific sequence of events for internal
        // state to remain valid. It's not an awesome arrangement, but it is currently
        // necessary.
        //
        // See: UserInputHandler.MouseDelegate#mHandledOnDown;
        mInputHandler.onSingleTapUp(SECONDARY_CLICK);

        mDetailsLookup.initAt(11);
        // Now we can send a subsequent event that should extend selection.
        mInputHandler.onDown(SHIFT_CLICK);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mSelection.assertRangeSelection(7, 11);
    }
}
