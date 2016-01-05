/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.internal.util.Preconditions.checkArgument;

import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView.AdapterDataObserver;
import android.util.SparseArray;
import android.view.ViewGroup;

import java.util.List;

/**
 * Adapter wrapper that inserts a sort of line break item between directories and regular files.
 * Only needs to be used in GRID mode...at this time.
 */
final class SectionBreakDocumentsAdapterWrapper extends DocumentsAdapter {

    private static final String TAG = "SectionBreakDocumentsAdapterWrapper";
    private static final int ITEM_TYPE_SECTION_BREAK = Integer.MAX_VALUE;

    private final Environment mEnv;
    private final DocumentsAdapter mDelegate;

    private int mBreakPosition = -1;

    SectionBreakDocumentsAdapterWrapper(Environment environment, DocumentsAdapter delegate) {
        mEnv = environment;
        mDelegate = delegate;

        // Events and information flows two ways between recycler view and adapter.
        // So we need to listen to events on our delegate and forward them
        // to our listeners with a corrected position.
        AdapterDataObserver adapterDataObserver = new AdapterDataObserver() {
            public void onChanged() {
                throw new UnsupportedOperationException();
            }

            public void onItemRangeChanged(int positionStart, int itemCount) {
                checkArgument(itemCount == 1);
            }

            public void onItemRangeInserted(int positionStart, int itemCount) {
                checkArgument(itemCount == 1);
                if (positionStart < mBreakPosition) {
                    mBreakPosition++;
                }
                notifyItemRangeInserted(toViewPosition(positionStart), itemCount);
            }

            public void onItemRangeRemoved(int positionStart, int itemCount) {
                checkArgument(itemCount == 1);
                if (positionStart < mBreakPosition) {
                    mBreakPosition--;
                }
                notifyItemRangeRemoved(toViewPosition(positionStart), itemCount);
            }

            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                throw new UnsupportedOperationException();
            }
        };

        mDelegate.registerAdapterDataObserver(adapterDataObserver);
    }

    public GridLayoutManager.SpanSizeLookup createSpanSizeLookup() {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // Make layout whitespace span the grid. This has the effect of breaking
                // grid rows whenever layout whitespace is encountered.
                if (getItemViewType(position) == ITEM_TYPE_SECTION_BREAK) {
                    return mEnv.getColumnCount();
                } else {
                    return 1;
                }
            }
        };
    }

    @Override
    public DocumentHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == ITEM_TYPE_SECTION_BREAK) {
            return new EmptyDocumentHolder(mEnv.getContext());
        } else {
            return mDelegate.createViewHolder(parent, viewType);
        }
    }

    @Override
    public void onBindViewHolder(DocumentHolder holder, int p, List<Object> payload) {
        if (holder.getItemViewType() != ITEM_TYPE_SECTION_BREAK) {
            mDelegate.onBindViewHolder(holder, toDelegatePosition(p), payload);
        }
    }

    @Override
    public void onBindViewHolder(DocumentHolder holder, int p) {
        if (holder.getItemViewType() != ITEM_TYPE_SECTION_BREAK) {
            mDelegate.onBindViewHolder(holder, toDelegatePosition(p));
        }
    }

    @Override
    public int getItemCount() {
        return mBreakPosition == -1
                ? mDelegate.getItemCount()
                : mDelegate.getItemCount() + 1;
    }

    @Override
    public void onModelUpdate(Model model) {
        mDelegate.onModelUpdate(model);
        mBreakPosition = -1;

        // Walk down the list of IDs till we encounter something that's not a directory, and
        // insert a whitespace element - this introduces a visual break in the grid between
        // folders and documents.
        // TODO: This code makes assumptions about the model, namely, that it performs a
        // bucketed sort where directories will always be ordered before other files. CBB.
        List<String> modelIds = mDelegate.getModelIds();
        for (int i = 0; i < modelIds.size(); i++) {
            if (!isDirectory(model, i)) {
                mBreakPosition = i;
                break;
            }
        }
    }

    @Override
    public void onModelUpdateFailed(Exception e) {
        mDelegate.onModelUpdateFailed(e);
    }

    @Override
    public int getItemViewType(int p) {
        if (p == mBreakPosition) {
            return ITEM_TYPE_SECTION_BREAK;
        } else {
            return mDelegate.getItemViewType(toDelegatePosition(p));
        }
    }

    /**
     * Returns the position of an item in the delegate, adjusting
     * values that are greater than the break position.
     *
     * @param p Position within the view
     * @return Position within the delegate
     */
    private int toDelegatePosition(int p) {
        return (mBreakPosition != -1 && p > mBreakPosition) ? p - 1 : p;
    }

    /**
     * Returns the position of an item in the view, adjusting
     * values that are greater than the break position.
     *
     * @param p Position within the delegate
     * @return Position within the view
     */
    private int toViewPosition(int p) {
        // If position is greater than or equal to the break, increase by one.
        return (mBreakPosition != -1 && p >= mBreakPosition) ? p + 1 : p;
    }

    @Override
    public SparseArray<String> hide(String... ids) {
        // NOTE: We hear about these changes and adjust break position
        // in our AdapterDataObserver.
        return mDelegate.hide(ids);
    }

    @Override
    void unhide(SparseArray<String> ids) {
        // NOTE: We hear about these changes and adjust break position
        // in our AdapterDataObserver.
        mDelegate.unhide(ids);
    }

    @Override
    List<String> getModelIds() {
        return mDelegate.getModelIds();
    }

    @Override
    String getModelId(int p) {
        return (p == mBreakPosition) ? null : mDelegate.getModelId(toDelegatePosition(p));
    }

    @Override
    public void notifyItemSelectionChanged(String id) {
        mDelegate.notifyItemSelectionChanged(id);
    }
}
