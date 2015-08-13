package com.p4rc.sdk;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.FrameLayout;

import com.p4rc.sdk.activity.MainActivity;
import com.p4rc.sdk.activity.SplashActivity;
import com.p4rc.sdk.model.GamePoint;
import com.p4rc.sdk.model.Point;
import com.p4rc.sdk.net.NetworkErrors;
import com.p4rc.sdk.task.BatchCheckinPointsTask;
import com.p4rc.sdk.task.CheckinPointsTask;
import com.p4rc.sdk.task.ConvertPointsTask;
import com.p4rc.sdk.task.CustomAsyncTask;
import com.p4rc.sdk.task.CustomAsyncTask.AsyncTaskListener;
import com.p4rc.sdk.task.MaxPointsTask;
import com.p4rc.sdk.task.PlayerPingTask;
import com.p4rc.sdk.task.PointsGameTask;
import com.p4rc.sdk.utils.AppUtils;
import com.p4rc.sdk.utils.Constants;
import com.p4rc.sdk.utils.JsonUtility;
import com.p4rc.sdk.utils.PointsManager;

public class P4RC {
	private static P4RC instance = null;
	
	public static final String INIT_COMPLETED_ACTION = "com.p4rc.sdk.action.INIT_COMPLETED";
	public static final String INIT_FAILED_ACTION= "com.p4rc.sdk.action.INIT_FAILED";
	public static final String POINT_SENT_ACTION= "com.p4rc.sdk.action.POINT_SENT";
	public static final String POINT_SENT_ERROR_ACTION= "com.p4rc.sdk.action.POINT_SENT_ERROR";
	
	public static final long PLAYER_PING_PERIOD = 24*60*60*1000; //in ms
	
	public static final long UPDATE_PING_PERIOD = 3600; // Unix time second 1 hour
	
	private Context context;
	
	private String apiKey;
	private String serverHost;
	
	private String gameRefId;
	
	@SuppressWarnings("unused")
	private boolean isInitialized;
	private boolean isLevelStarted;
	
	@SuppressWarnings("unused")
	private boolean isSessionIdValid = true;

	private PointsManager pointsManager;
	private AppConfig appConfig;
	
	private AlertDialog requestDialog;
	
	private P4RC(){ 
		
	}
	
	public static P4RC getInstance() {
      if (instance == null) {
          synchronized (P4RC.class) {
              if (instance == null) {
                  instance = new P4RC();
              }
          }
      }
	  return instance;
	}
	
	
	public Context getContext() {
		return context;
	}

	public void setContext(Context context) {
		this.context = context;
	}

	public boolean isLevelStarted() {
		return isLevelStarted;
	}
	
	public void initialize(String gameRefId, String apiKey){
		
		if (TextUtils.isEmpty(apiKey)){						
			context.sendBroadcast(new Intent(INIT_FAILED_ACTION));
			isInitialized = false;
			return;
		}
	
		this.apiKey = apiKey; 
		this.gameRefId = gameRefId;
		this.serverHost = AppConfig.PRODUCTION_SERVER_HOST;
		
		appConfig = AppConfig.getInstance();
		pointsManager = PointsManager.getInstance(context);

		appConfig.setContext(context);
		pointsManager.setContext(context);		
		
		appConfig.setAPIBaseUrl(serverHost+"/services/");
		appConfig.setHtmlPageUrl(serverHost+"/mIndex.html");
		
		isInitialized = true;
		
		context.sendBroadcast(new Intent(INIT_COMPLETED_ACTION));
	}
	
	// image for P4RC button
	public int imageResForButton(){
		return R.drawable.logo_main_menu;
	}

	// should be sent, when game is restarted and game points being reset
	public void gameWasRestarted(){
		pointsManager.resetGamePOints();
		isLevelStarted = false;
	}	
	
	// should be sent when P4RC button is pressed	
	public void  showMainP4RCPage(){
		if (!AppUtils.isOnline(context)){
			AppUtils.showAlert(context, "P4RC Service is unreachable. Please make sure your network connection is enabled.");
			return;
		}
		
		if(isLoggedIn()) {
			// send game points after off line mode
			final ArrayList<GamePoint> gamePointsArrayList = PointsManager.getInstance(AppConfig.getInstance().getContext()).getGamePointsTable();

			if (gamePointsArrayList != null && gamePointsArrayList.size() > 0) {
					BatchCheckinPointsTask batchTask = new BatchCheckinPointsTask(
							context, false);
					batchTask
							.setAsyncTaskListener(new BatchTaskListener());
					batchTask.execute();
			} else {
				context.startActivity(new Intent(context, MainActivity.class));
			}
		}else{
			context.startActivity(new Intent(context, MainActivity.class));
		}
	}		
	
	public void  showSplash(){
		context.startActivity(new Intent(context, SplashActivity.class));
	}
	
	// is user logged in
	/**
	 * Responsible for check if user is already logged in. 
	 * This method checks P4RCCacheManager�s sessionId value. 
	 * If it is not empty or null, user is logged in. 
	 * @return TRUE if we have saved session ID
	 */
	public boolean isLoggedIn(){
		String sessionID = AppConfig.getInstance().getSessionId();
		return sessionID != null && !Constants.EMPTY_STRING.equals(sessionID) /*&& isSessionIdValid*/;
	}
	
	// reset cached session id and cookie
	/**
	 * Sets P4RCCacheManager�s session cookie and session id values to null. 
	 * This operation also clears all stored information. (P4RCPointsManager also 
	 * rewrites it�s file with session information on disk.)
	 * P4RCPointsManager resets it�s stored p4rc points - last p4rc points and total ones.
	 */
	public void logout() {
		AppConfig.getInstance().setSessionId(null);
		pointsManager.resetAllGamePoints();
		AppUtils.clearCookies(context);
		pointsManager.saveGamePoints();
		isSessionIdValid = false;
	}
	
	// should be sent, when user complete some level
	/**
	 * Method, which should be called by the game right after level completion.
	 * It refreshes the information about last and total game points, level end 
	 * date and others. Also it adds game points and level to cached points.
	 * @param level last finished level value
	 * @param totalGamePoints game points earned by the user per last game level
	 */
	public void didCompleteLevelWithTotalPoints(int level,  int totalGamePoints) {
		if(isLevelStarted) {
			GamePoint levelPoint;
			pointsManager.setLastLevelValue(level);
			pointsManager.setLastGamePoints(totalGamePoints);
			pointsManager.setTotalGamePoints(pointsManager.getTotalGamePoints() 
					+ totalGamePoints);
			long lastLevelEndTime = AppUtils.getTimeInGMT();
			
			levelPoint = new GamePoint(pointsManager.getLastLevelStartTime(),
					lastLevelEndTime, level, GamePoint.calculatePlayedTime(pointsManager.
							getLastLevelStartTime(), lastLevelEndTime), totalGamePoints,
							AppUtils.getTimeInGMT());
			pointsManager.addGamePoint(levelPoint);
			pointsManager.saveGamePoints();
			
			if(pointsManager.isPointsTableExists() && AppUtils.isOnline(context)) {
				pointsManager.convertPointsToP4RCPoints(totalGamePoints, level);
				if(isLoggedIn()) {
					CheckinPointsTask checkIn = new CheckinPointsTask(context, false);
					checkIn.setAsyncTaskListener(new PointsCheckinListener());
					checkIn.execute(pointsManager.getLastLevelValue(), totalGamePoints);
				} //we do nothing if user not logged in because level points already saved to store
			} else if(AppUtils.isOnline(context)) {
				ConvertPointsTask convertionTask = new ConvertPointsTask(context, false);
				convertionTask.setAsyncTaskListener(new ServerConvertionListener());
				convertionTask.execute(totalGamePoints, level, 
						GamePoint.calculatePlayedTime(pointsManager.getLastLevelStartTime(),
								lastLevelEndTime), isLoggedIn());
			} else {
				pointsManager.convertPointsToP4RCPoints(totalGamePoints, level);
				pointsManager.saveGamePoints();
				
			}
			isLevelStarted = false;
		}
	}
	
	//should be sent, when user complete some level
	/**
	 * Method, which should be called by the game right after level completion.
	 * It refreshes the information about last and total game points, level end 
	 * date and others. Also it adds game points and level to cached points.
	 * @param level last finished level value
	 * @param levelGamePoints game points earned by the user per last game level
	 */
	public void didCompleteLevelWithPoints(int level , int levelGamePoints) {
		if(isLevelStarted) {
			GamePoint levelPoint;
			pointsManager.setLastGamePoints(levelGamePoints);
			pointsManager.setTotalGamePoints(pointsManager.getTotalGamePoints() 
					+ levelGamePoints);
			pointsManager.setLastLevelValue(level);
			long lastLevelEndTime = AppUtils.getTimeInGMT();
			
			levelPoint = new GamePoint(pointsManager.getLastLevelStartTime(),
					lastLevelEndTime, level, GamePoint.calculatePlayedTime(pointsManager.
							getLastLevelStartTime(), lastLevelEndTime), levelGamePoints,
							AppUtils.getTimeInGMT());
			pointsManager.addGamePoint(levelPoint);
			
			pointsManager.saveGamePoints();
			
			if(pointsManager.isPointsTableExists() && AppUtils.isOnline(context)) {
				pointsManager.convertPointsToP4RCPoints(levelGamePoints, level);
				if(isLoggedIn()) {
					CheckinPointsTask checkIn = new CheckinPointsTask(context, false);
					checkIn.setAsyncTaskListener(new PointsCheckinListener());
					checkIn.execute(pointsManager.getLastLevelValue(), levelGamePoints);
				} //we do nothing if user not logged in because level points already saved to store
			} else if(AppUtils.isOnline(context)) {
				ConvertPointsTask convertionTask = new ConvertPointsTask(context, false);
				convertionTask.setAsyncTaskListener(new ServerConvertionListener());
				convertionTask.execute(levelGamePoints, level, 
						GamePoint.calculatePlayedTime(pointsManager.getLastLevelStartTime(),
								lastLevelEndTime), isLoggedIn());
			} else {
				pointsManager.convertPointsToP4RCPoints(levelGamePoints, level);
				pointsManager.saveGamePoints();
			}
			isLevelStarted = false;
		}
	}
	
	//should be sent, when user start some level
	public void didStartLevel() {
		final long currentTime = AppUtils.getTimeInGMT();
		pointsManager.setLastLevelStartTime(currentTime);
		
		isLevelStarted = true;
		
		if (!AppUtils.isOnline(context)){
			return;
		}

		long unixTimeNow = AppUtils.getCurrentTimeMiliss();
		
		if(PointsManager.getInstance(context).getLastPlayerUnixTime() == -1){
			PointsManager.getInstance(context).setLastPlayerUnixTime(unixTimeNow);
		} else {
			if((unixTimeNow - PointsManager.getInstance(context).getLastPlayerUnixTime()) > UPDATE_PING_PERIOD){
				receivePointsTable();
				PointsManager.getInstance(context).setLastPlayerUnixTime(unixTimeNow);
			}
		}

		final int lastPlayerPingDay = PointsManager.getInstance(context).getLastPlayerPingDay();
		@SuppressWarnings("deprecation")
		final int currentDay = (new Date(currentTime)).getDay(); 
		
		if (currentDay!= lastPlayerPingDay){
			PlayerPingTask playerPingTask = new PlayerPingTask(context);
			playerPingTask.setShowProgress(false);
			playerPingTask.setAsyncTaskListener( new AsyncTaskListener() {

				@Override
				public void onBeforeTaskStarted(CustomAsyncTask<?, ?, ?> task) {}

				@Override
				public void onTaskFinished(CustomAsyncTask<?, ?, ?> task) {
					if((Boolean) task.getResult()){
						if(context != null) {
							if(JsonUtility.SUCCESS_STATUS.equals(((PlayerPingTask)task).
									getData().get(JsonUtility.STATUS_PARAM))) {
								
								PointsManager.getInstance(context).setLastPlayerPingDay(currentDay);					
								
								receivePointsTable();
							}
						}
					}
				}
			});
			playerPingTask.execute(isLoggedIn());
		}
	}
	
	//last earned P4RC points by current user
	public int lastP4RCPoints(){
		return pointsManager.getLastP4RCPoints();
	}	
	
	//total earned P4RC points by current user
	public int totalP4RCPoints(){
		return pointsManager.getTotalP4RCPoints();
	};	
	
	public PointsManager getPointsManager(){
		return pointsManager;
	}
	
	public void showDescriptiveAlertView(){
		AppUtils.showAlert(context, "P4RC",
				String.format("Total game points: %d \nLast game points: %d" +
				"\nTotal P4RC points: %d \nLast P4RC points: %d",
				pointsManager.getTotalGamePoints(),
				pointsManager.getLastGamePoints(),
				pointsManager.getTotalP4RCPoints(),
				pointsManager.getLastP4RCPoints()));
		
	}
	
	public void p4rcRequestDialog(){
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		
		final FrameLayout view = (FrameLayout) inflater.inflate(R.layout.dialog_p4rc_request, null);
		
		view.findViewById(R.id.yes_button).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				showMainP4RCPage();
				requestDialog.dismiss();					
			}
		});
		
		view.findViewById(R.id.no_button).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				requestDialog.dismiss();	
			}
		});
		
		builder.setInverseBackgroundForced(true);
		requestDialog = builder.create();
		requestDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		requestDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		requestDialog.show();
		requestDialog.setContentView(view);
		builder.setView(view);

	}
	
	void tableReceived (Map<Integer,Point> pointsTable){
		
		pointsTable = PointsManager.getInstance(context).getPointsTable();
	}

	public String getGameRefId() {
		return gameRefId;
	}

	public String getApiKey() {
		return apiKey;
	}

	public String getServerHost() {
		return serverHost;
	}
	
	private void receivePointsTable() {
		PointsGameTask pointsGameTask = new PointsGameTask(context);
		pointsGameTask.setShowProgress(false);
		pointsGameTask.setAsyncTaskListener(new AsyncTaskListener() {

			@Override
			public void onBeforeTaskStarted(CustomAsyncTask<?, ?, ?> task) {}

			@Override
			public void onTaskFinished(CustomAsyncTask<?, ?, ?> task) {
				if ((Boolean) task.getResult()) {
					Map<String, Object> data = ((PointsGameTask) task)
							.getData();
					@SuppressWarnings("unchecked")
					HashMap<Integer, Point> pointsTable = (HashMap<Integer, Point>) data
							.get(JsonUtility.POINTS_TABLE_PARAM);
					if(context != null) {
						PointsManager.getInstance(context).setPointsTable(
								pointsTable);
						PointsManager.getInstance(context).savePoints();
					}
				}
				receiveMaxPointsValue();
			}
		});
		pointsGameTask.execute();
	}
	
	private void receiveMaxPointsValue() {
		MaxPointsTask maxPointTask = new MaxPointsTask(context, false);
		maxPointTask.setAsyncTaskListener(new AsyncTaskListener() {

			@Override
			public void onBeforeTaskStarted(CustomAsyncTask<?, ?, ?> task) {}

			@Override
			public void onTaskFinished(CustomAsyncTask<?, ?, ?> task) {
				if ((Boolean) task.getResult()) {
					Map<String, Object> responseData = ((MaxPointsTask) task)
							.getData();
					if (JsonUtility.SUCCESS_STATUS.equals(responseData
							.get(JsonUtility.STATUS_PARAM))) {
						int maxPoints = (Integer) responseData
								.get(JsonUtility.P4RC_POINTS);
						PointsManager.getInstance(context).setMaxPoints(
								maxPoints);
					} else {
						PointsManager.getInstance(context).setMaxPoints(
								AppConfig.DEFAULT_MAX_POINTS);
					}
				} else {
					PointsManager.getInstance(context).setMaxPoints(
							AppConfig.DEFAULT_MAX_POINTS);
				}
			}
		});
		maxPointTask.execute();
	}
	
	private class ServerConvertionListener implements CustomAsyncTask.AsyncTaskListener {
		
		@Override
		public void onBeforeTaskStarted(CustomAsyncTask<?, ?, ?> task) {}

		@Override
		public void onTaskFinished(CustomAsyncTask<?, ?, ?> task) {
			if((Boolean)task.getResult()) {
				HashMap<String, Object> data = ((ConvertPointsTask)task).getData();
				if(data != null && JsonUtility.SUCCESS_STATUS.equals(
						data.get(JsonUtility.STATUS_PARAM))) {
					pointsManager.saveGamePoints();
					isLevelStarted = false;
					if(isLoggedIn()) {
						CheckinPointsTask checkIn = new CheckinPointsTask(context, false);
						checkIn.setAsyncTaskListener(new PointsCheckinListener());
						checkIn.execute(pointsManager.getLastLevelValue(), pointsManager.getLastGamePoints());
					}
				} else if(data != null) {
					Integer code = (Integer) data.get(JsonUtility.CODE_PARAM);
					//temporary
					if(code == NetworkErrors.INVALID_SESSION_ID_ERR) {
						logout();
					}
				}
			}
		}
	}
	
	private class PointsCheckinListener implements CustomAsyncTask.AsyncTaskListener {

		@Override
		public void onBeforeTaskStarted(CustomAsyncTask<?, ?, ?> task) {}

		@Override
		public void onTaskFinished(CustomAsyncTask<?, ?, ?> task) {

			isLevelStarted = false;
			
			if((Boolean)task.getResult() == true){
				HashMap<String, Object> data = ((CheckinPointsTask)task).getData();
				if(data != null &&JsonUtility.SUCCESS_STATUS.equals(
						data.get(JsonUtility.STATUS_PARAM))) {
					pointsManager.removeLevelPointsData(pointsManager.getLastLevelStartTime());
					pointsManager.saveGamePoints();
				} else {
					if(data != null) {
						Integer code = (Integer) data.get(JsonUtility.CODE_PARAM);
						//temporary
						if(code == NetworkErrors.INVALID_SESSION_ID_ERR) {
							logout();
						}
					}
				}
				context.sendBroadcast(new Intent(POINT_SENT_ACTION));
			}else{
				context.sendBroadcast(new Intent(POINT_SENT_ERROR_ACTION));
			}
		}
	}
	
	private class BatchTaskListener implements
			CustomAsyncTask.AsyncTaskListener {
		
		@Override
		public void onBeforeTaskStarted(CustomAsyncTask<?, ?, ?> task) {}
		
		@Override
		public void onTaskFinished(CustomAsyncTask<?, ?, ?> task) {
			HashMap<String, Object> responseData;
			
			//starting webView after points sending
			context.startActivity(new Intent(context, MainActivity.class));
			
			if (task != null) {
				responseData = ((BatchCheckinPointsTask) task).getData();
				if (JsonUtility.SUCCESS_STATUS.equals(responseData
						.get(JsonUtility.STATUS_PARAM))) {
					PointsManager
							.getInstance(
									AppConfig.getInstance().getContext())
							.getGamePointsTable().clear();
					PointsManager.getInstance(
							AppConfig.getInstance().getContext())
							.saveGamePoints();
				} else if(responseData != null) {
					Integer code = (Integer) responseData.get(JsonUtility.CODE_PARAM);
					//temporary
					if(code != null && code == NetworkErrors.INVALID_SESSION_ID_ERR) {
						logout();
					}
				}
			}
		}
	}
}
