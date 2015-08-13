package com.p4rc.sdk.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.widget.EditText;


public class ValidationUtils {

	public static final String EMPTY_STRING = "";
	public static final String ONE_SPACE = " ";
	public static final String EMAIL_REG_EXP = "[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})";
	
	public static boolean checkRequiredField(EditText editText) {
		String value = editText.getText().toString();
		if (value == null || EMPTY_STRING.equals(value)) {
			editText.requestFocus();
			return false;
		}
		return true;
	}
	
	public static boolean checkEmail(EditText editText) {
		String value = editText.getText().toString();
		Pattern pattern = Pattern.compile(EMAIL_REG_EXP);
		Matcher matcher = pattern.matcher(value);
		return matcher.matches();
	}
	
	public static boolean checkEmail(String cardNumber) {
		Pattern pattern = Pattern.compile(EMAIL_REG_EXP);
		Matcher matcher = pattern.matcher(cardNumber);
		return matcher.matches();
	}
}
