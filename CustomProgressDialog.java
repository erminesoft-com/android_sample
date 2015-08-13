package com.p4rc.sdk.view;

import com.p4rc.sdk.R;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.ViewGroup.LayoutParams;
import android.widget.ProgressBar;

public class CustomProgressDialog extends Dialog {
	public static CustomProgressDialog show(Context context, CharSequence title,
			CharSequence message) {
		return show(context, title, message, false);
	}

	public static CustomProgressDialog show(Context context, CharSequence title,
			CharSequence message, boolean indeterminate) {
		return show(context, title, message, indeterminate, false, null);
	}

	public static CustomProgressDialog show(Context context, CharSequence title,
			CharSequence message, boolean indeterminate, boolean cancelable) {
		return show(context, title, message, indeterminate, cancelable, null);
	}

	public static CustomProgressDialog show(Context context, CharSequence title,
			CharSequence message, boolean indeterminate, boolean cancelable,
			OnCancelListener cancelListener) {
		CustomProgressDialog dialog = new CustomProgressDialog(context);
		if (context instanceof Activity){
			dialog.setOwnerActivity((Activity)context);
		}
		dialog.setTitle(title);
		dialog.setCancelable(cancelable);
		dialog.setOnCancelListener(cancelListener);
		dialog.addContentView(new ProgressBar(context), new LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		dialog.show();

		return dialog;
	}

	public CustomProgressDialog(Context context) {
		super(context, R.style.CustomProgressDialog);
	}

}
