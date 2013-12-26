package com.android.example;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class FlipAdapter extends BaseAdapter {
	
	public interface PageChangeListener {
		public void onPageRequested(int page);
	}

	private LayoutInflater inflater;
	private int count = 8;

	public FlipAdapter(Context context) {
		inflater = LayoutInflater.from(context);
	}
	
	public class ViewHolder {
		TextView text;
		TextView txtHeader;
		TextView txtFooter;
	}

	@Override
	public int getCount() {
		return count;
	}

	@Override
	public Object getItem(int position) {
		return position;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		if (convertView == null) {
			holder = new ViewHolder();
			convertView = inflater.inflate(R.layout.page, parent, false);
			holder.txtHeader = (TextView) convertView.findViewById(R.id.abhan_header);
			holder.text = (TextView) convertView.findViewById(R.id.text);
			holder.txtFooter = (TextView) convertView.findViewById(R.id.abhan_footer);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}
		holder.text.setText(String.valueOf(position));

		return convertView;
	}
}