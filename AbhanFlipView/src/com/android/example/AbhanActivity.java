package com.android.example;

import android.app.Activity;
import android.os.Bundle;
import com.android.example.flips.FlipView;
import com.android.example.flips.FlipView.OnFlipListener;

public class AbhanActivity extends Activity implements OnFlipListener {
	
	private FlipView flipView;
	private FlipAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_abhan);
		
		flipView = (FlipView) findViewById(R.id.flip_view);
		adapter = new FlipAdapter(this);
		flipView.setAdapter(adapter);
		flipView.setOnFlipListener(this);
		flipView.peakNext(false);
	}

	@Override
	public void onFlippedToPage(FlipView view, int position, long id) {
		android.util.Log.i("Abhan", "I am called");
	}
}