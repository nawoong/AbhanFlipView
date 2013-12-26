package com.android.example.flips;

import android.util.SparseArray;
import android.view.View;

public class Recycler {

	private SparseArray<View>[] scrapViews;
	private int viewTypeCount;
	private SparseArray<View> currentScrapViews;

	void setViewTypeCount(int viewTypeCount) {
		if (viewTypeCount < 1) {
			throw new IllegalArgumentException("Can't have a viewTypeCount < 1");
		}

		@SuppressWarnings("unchecked")
		SparseArray<View>[] scrapViews = new SparseArray[viewTypeCount];
		for (int i = 0; i < viewTypeCount; i++) {
			scrapViews[i] = new SparseArray<View>();
		}
		this.viewTypeCount = viewTypeCount;
		currentScrapViews = scrapViews[0];
		this.scrapViews = scrapViews;
	}

	View getScrapView(int position, int viewType) {
		if (viewTypeCount == 1) {
			return retrieveFromScrap(currentScrapViews, position);
		} else if (viewType >= 0 && viewType < scrapViews.length) {
			return retrieveFromScrap(scrapViews[viewType], position);
		}
		return null;
	}

	void addScrapView(View scrap, int position, int viewType) {
		if (viewTypeCount == 1) {
			currentScrapViews.put(position, scrap);
		} else {
			scrapViews[viewType].put(position, scrap);
		}
	}

	static View retrieveFromScrap(SparseArray<View> scrapViews, int position) {
		int size = scrapViews.size();
		if (size > 0) {
			View result = scrapViews.get(position, null);
			if (result != null) {
				scrapViews.remove(position);
				return result;
			}
			
			int index = size - 1;
			result = scrapViews.valueAt(index);
			scrapViews.remove(scrapViews.keyAt(index));

			return result;
		}
		return null;
	}
}