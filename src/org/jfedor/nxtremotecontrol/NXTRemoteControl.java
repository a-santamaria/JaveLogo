/*
 * Copyright (c) 2010 Jacek Fedorynski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This file is derived from:
 * 
 * http://developer.android.com/resources/samples/BluetoothChat/src/com/example/android/BluetoothChat/BluetoothChat.html
 * 
 * Copyright (c) 2009 The Android Open Source Project
 */

package org.jfedor.nxtremotecontrol;

/*
 * TODO:
 * 
 * tilt controls
 */

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Stack;

import org.jfedor.nxtremotecontrol.model.CustomListViewAdapter;
import org.jfedor.nxtremotecontrol.model.NXTInstruction;
import org.jfedor.nxtremotecontrol.view.Tank3MotorView;
import org.jfedor.nxtremotecontrol.view.TankView;
import org.jfedor.nxtremotecontrol.view.TouchPadView;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.LauncherActivity.ListItem;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class NXTRemoteControl extends Activity implements
		OnSharedPreferenceChangeListener {

	private boolean NO_BT = false;

	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_CONNECT_DEVICE = 2;
	private static final int REQUEST_SETTINGS = 3;

	public static final int MESSAGE_TOAST = 1;
	public static final int MESSAGE_STATE_CHANGE = 2;

	public static final String TOAST = "toast";

	private static final int MODE_BUTTONS = 1;
	private static final int MODE_TOUCHPAD = 2;
	private static final int MODE_TANK = 3;
	private static final int MODE_TANK3MOTOR = 4;
	private static final int MODE_PROGRAM = 5;

	private BluetoothAdapter mBluetoothAdapter;
	private PowerManager mPowerManager;
	private PowerManager.WakeLock mWakeLock;
	private NXTTalker mNXTTalker;

	private int mState = NXTTalker.STATE_NONE;
	private int mSavedState = NXTTalker.STATE_NONE;
	private boolean mNewLaunch = true;
	private String mDeviceAddress = null;
	private TextView mStateDisplay;
	private Button mConnectButton;
	private Button mDisconnectButton;
	private TouchPadView mTouchPadView;
	private TankView mTankView;
	private Tank3MotorView mTank3MotorView;
	private Menu mMenu;
	private boolean pencilDown;

	private int mPower = 80;
	private int mControlsMode = MODE_BUTTONS;

	private boolean mReverse;
	private boolean mReverseLR;
	private boolean mRegulateSpeed;
	private boolean mSynchronizeMotors;


	private ArrayAdapter<NXTInstruction> commandsList;
	private ListView listView;

	/** Called when the activity is first created. */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		readPreferences(prefs, null);
		prefs.registerOnSharedPreferenceChangeListener(this);

		if (savedInstanceState != null) {
			mNewLaunch = false;
			mDeviceAddress = savedInstanceState.getString("device_address");
			if (mDeviceAddress != null) {
				mSavedState = NXTTalker.STATE_CONNECTED;
			}

			if (savedInstanceState.containsKey("power")) {
				mPower = savedInstanceState.getInt("power");
			}
			if (savedInstanceState.containsKey("controls_mode")) {
				mControlsMode = savedInstanceState.getInt("controls_mode");
			}
		}

		mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
				| PowerManager.ON_AFTER_RELEASE, "NXT Remote Control");

		if (!NO_BT) {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

			if (mBluetoothAdapter == null) {
				Toast.makeText(this, "Bluetooth is not available",
						Toast.LENGTH_LONG).show();
				finish();
				return;
			}
		}

		setupUI();

		mNXTTalker = new NXTTalker(mHandler);
	}

	private class ClearList implements OnClickListener {
		private List<NXTInstruction> commandList;

		public ClearList(List<NXTInstruction> list) {
			this.commandList = list;
		}

		@Override
		public void onClick(View v) {
			commandList.clear();
			listView = (ListView) findViewById(R.id.commands_list);
			CustomListViewAdapter adapter = funcaux(commandList);
			listView.setAdapter(adapter);

		}

	}

	private class ProgramButtonOnTouchListener implements OnTouchListener {
		private ArrayAdapter<NXTInstruction> commandsList;
		private NXTInstruction command;

		public ProgramButtonOnTouchListener(
				ArrayAdapter<NXTInstruction> commandsList,
				NXTInstruction command) {
			this.commandsList = commandsList;
			this.command = command;
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			// Log.i("NXT", "onTouch event: " +
			// Integer.toString(event.getAction()));
			int action = event.getAction();
			// if ((action == MotionEvent.ACTION_DOWN) || (action ==
			// MotionEvent.ACTION_MOVE)) {
			if (action == MotionEvent.ACTION_DOWN) {
				this.commandsList.add(this.command);
			}
			return true;
		}
	}

	@SuppressLint("NewApi")
	private class CustomProgramButtonOnTouchListener implements OnTouchListener {
		private ArrayList<NXTInstruction> commandsList;
		private NXTInstruction command;
		private Activity parent;
		 

		public CustomProgramButtonOnTouchListener(
				ArrayList<NXTInstruction> commandsList, NXTInstruction command, Activity parent) {
			this.commandsList = commandsList;
			this.command = command;
			this.parent = parent;
			Log.i("NUm, Descp", command.duracion + " " + command.nombre);
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			// Log.i("NXT", "onTouch event: " +
			// Integer.toString(event.getAction()));
			int action = event.getAction();
			// if ((action == MotionEvent.ACTION_DOWN) || (action ==
			// MotionEvent.ACTION_MOVE)) {
			if (action == MotionEvent.ACTION_DOWN) {
				if (command.duracion == 7) {
					AlertDialog.Builder alert = new AlertDialog.Builder(parent);

					alert.setTitle("Ciclo");
					alert.setMessage("N�mero de Repeticiones");

					// Set an EditText view to get user input 
					final EditText input;
					input = new EditText(parent);
					input.setInputType(InputType.TYPE_CLASS_NUMBER);
					alert.setView(input);

					alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							String value = input.getText()+"";
							if(value.isEmpty()) return;
							
							Log.i("devovio ", value);
							NXTInstruction command1 = new NXTInstruction(command);
							command1.repeticiones = Integer.parseInt(value);
							command1.nombre = "Ciclo (" + command1.repeticiones	+ ")";
							commandsList.add(command1);
							Log.i("agrege ", command1.nombre + "");
							listView = (ListView) findViewById(R.id.commands_list);
							CustomListViewAdapter adapter = funcaux(commandsList);
							listView.setAdapter(adapter);
							input.setInputType(0);
							input.setInputType(InputType.TYPE_CLASS_NUMBER);
							
						 }
					});

					alert.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
					 public void onClick(DialogInterface dialog, int whichButton) {
					     // Canceled.
					}
					});

					alert.show();
					
				}else {

						this.commandsList.add(this.command);
						Log.i("agrege ", this.command.nombre + "");
						listView = (ListView) findViewById(R.id.commands_list);
						CustomListViewAdapter adapter = funcaux(commandsList);
						listView.setAdapter(adapter);
						
				}
				
			}
			return true;
		}
	}

	public CustomListViewAdapter funcaux(List<NXTInstruction> commandsList) {
		CustomListViewAdapter adapter = new CustomListViewAdapter(this,
				R.layout.list_item, commandsList, 1);
		return adapter;
	}

	private class DirectionButtonOnTouchListener implements OnTouchListener {

		private double lmod;
		private double rmod;

		public DirectionButtonOnTouchListener(double l, double r) {
			lmod = l;
			rmod = r;
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			// Log.i("NXT", "onTouch event: " +
			// Integer.toString(event.getAction()));
			int action = event.getAction();
			// if ((action == MotionEvent.ACTION_DOWN) || (action ==
			// MotionEvent.ACTION_MOVE)) {
			if (action == MotionEvent.ACTION_DOWN) {
				byte power = (byte) mPower;
				if (mReverse) {
					power *= -1;
				}
				byte l = (byte) (power * lmod);
				byte r = (byte) (power * rmod);
				if (!mReverseLR) {
					mNXTTalker.motors(l, r, mRegulateSpeed, mSynchronizeMotors);
				} else {
					mNXTTalker.motors(r, l, mRegulateSpeed, mSynchronizeMotors);
				}
			} else if ((action == MotionEvent.ACTION_UP)
					|| (action == MotionEvent.ACTION_CANCEL)) {
				mNXTTalker.motors((byte) 0, (byte) 0, mRegulateSpeed,
						mSynchronizeMotors);
			}
			return true;
		}
	}

	private class TankOnTouchListener implements OnTouchListener {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			TankView tv = (TankView) v;
			float y;
			int action = event.getAction();
			if ((action == MotionEvent.ACTION_DOWN)
					|| (action == MotionEvent.ACTION_MOVE)) {
				byte l = 0;
				byte r = 0;
				for (int i = 0; i < event.getPointerCount(); i++) {
					y = -1.0f * (event.getY(i) - tv.mZero) / tv.mRange;
					if (y > 1.0f) {
						y = 1.0f;
					}
					if (y < -1.0f) {
						y = -1.0f;
					}
					if (event.getX(i) < tv.mWidth / 2f) {
						l = (byte) (y * 100);
					} else {
						r = (byte) (y * 100);
					}
				}
				if (mReverse) {
					l *= -1;
					r *= -1;
				}
				if (!mReverseLR) {
					mNXTTalker.motors(l, r, mRegulateSpeed, mSynchronizeMotors);
				} else {
					mNXTTalker.motors(r, l, mRegulateSpeed, mSynchronizeMotors);
				}
			} else if ((action == MotionEvent.ACTION_UP)
					|| (action == MotionEvent.ACTION_CANCEL)) {
				mNXTTalker.motors((byte) 0, (byte) 0, mRegulateSpeed,
						mSynchronizeMotors);
			}
			return true;
		}
	}

	private class Tank3MotorOnTouchListener implements OnTouchListener {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			Tank3MotorView t3v = (Tank3MotorView) v;
			float x;
			float y;
			int action = event.getAction();
			if ((action == MotionEvent.ACTION_DOWN)
					|| (action == MotionEvent.ACTION_MOVE)) {
				byte l = 0;
				byte r = 0;
				byte a = 0;
				for (int i = 0; i < event.getPointerCount(); i++) {
					y = -1.0f * (event.getY(i) - t3v.mZero) / t3v.mRange;
					if (y > 1.0f) {
						y = 1.0f;
					}
					if (y < -1.0f) {
						y = -1.0f;
					}
					x = event.getX(i);
					if (x < t3v.mWidth / 3f) {
						l = (byte) (y * 100);
					} else if (x > 2 * t3v.mWidth / 3f) {
						r = (byte) (y * 100);
					} else {
						a = (byte) (y * 100);
					}
				}
				if (mReverse) {
					l *= -1;
					r *= -1;
					a *= -1;
				}
				if (!mReverseLR) {
					mNXTTalker.motors3(l, r, a, mRegulateSpeed,
							mSynchronizeMotors);
				} else {
					mNXTTalker.motors3(r, l, a, mRegulateSpeed,
							mSynchronizeMotors);
				}
			} else if ((action == MotionEvent.ACTION_UP)
					|| (action == MotionEvent.ACTION_CANCEL)) {
				mNXTTalker.motors3((byte) 0, (byte) 0, (byte) 0,
						mRegulateSpeed, mSynchronizeMotors);
			}
			return true;
		}
	}

	private class TouchpadOnTouchListener implements OnTouchListener {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			TouchPadView tpv = (TouchPadView) v;
			float x, y, power;
			int action = event.getAction();
			if ((action == MotionEvent.ACTION_DOWN)
					|| (action == MotionEvent.ACTION_MOVE)) {
				x = (event.getX() - tpv.mCx) / tpv.mRadius;
				y = -1.0f * (event.getY() - tpv.mCy);
				if (y > 0f) {
					y -= tpv.mOffset;
					if (y < 0f) {
						y = 0.01f;
					}
				} else if (y < 0f) {
					y += tpv.mOffset;
					if (y > 0f) {
						y = -0.01f;
					}
				}
				y /= tpv.mRadius;
				float sqrt22 = 0.707106781f;
				float nx = x * sqrt22 + y * sqrt22;
				float ny = -x * sqrt22 + y * sqrt22;
				power = (float) Math.sqrt(nx * nx + ny * ny);
				if (power > 1.0f) {
					nx /= power;
					ny /= power;
					power = 1.0f;
				}
				float angle = (float) Math.atan2(y, x);
				float l, r;
				if (angle > 0f && angle <= Math.PI / 2f) {
					l = 1.0f;
					r = (float) (2.0f * angle / Math.PI);
				} else if (angle > Math.PI / 2f && angle <= Math.PI) {
					l = (float) (2.0f * (Math.PI - angle) / Math.PI);
					r = 1.0f;
				} else if (angle < 0f && angle >= -Math.PI / 2f) {
					l = -1.0f;
					r = (float) (2.0f * angle / Math.PI);
				} else if (angle < -Math.PI / 2f && angle > -Math.PI) {
					l = (float) (-2.0f * (angle + Math.PI) / Math.PI);
					r = -1.0f;
				} else {
					l = r = 0f;
				}
				l *= power;
				r *= power;
				if (mReverse) {
					l *= -1;
					r *= -1;
				}
				if (!mReverseLR) {
					mNXTTalker.motors((byte) (100 * l), (byte) (100 * r),
							mRegulateSpeed, mSynchronizeMotors);
				} else {
					mNXTTalker.motors((byte) (100 * r), (byte) (100 * l),
							mRegulateSpeed, mSynchronizeMotors);
				}
			} else if ((action == MotionEvent.ACTION_UP)
					|| (action == MotionEvent.ACTION_CANCEL)) {
				mNXTTalker.motors((byte) 0, (byte) 0, mRegulateSpeed,
						mSynchronizeMotors);
			}
			return true;
		}
	}

	private void updateMenu(int disabled) {
		if (mMenu != null) {
			mMenu.findItem(R.id.menuitem_buttons)
					.setEnabled(disabled != R.id.menuitem_buttons)
					.setVisible(disabled != R.id.menuitem_buttons);
			mMenu.findItem(R.id.menuitem_touchpad)
					.setEnabled(disabled != R.id.menuitem_touchpad)
					.setVisible(disabled != R.id.menuitem_touchpad);
			mMenu.findItem(R.id.menuitem_tank)
					.setEnabled(disabled != R.id.menuitem_tank)
					.setVisible(disabled != R.id.menuitem_tank);
			mMenu.findItem(R.id.menuitem_tank3motor)
					.setEnabled(disabled != R.id.menuitem_tank3motor)
					.setVisible(disabled != R.id.menuitem_tank3motor);
			mMenu.findItem(R.id.menuitem_program)
					.setEnabled(disabled != R.id.menuitem_program)
					.setVisible(disabled != R.id.menuitem_program);
		}
	}

	private void setupUI() {
		if (mControlsMode == MODE_PROGRAM) {
			pencilDown = false;

			setContentView(R.layout.program);

			updateMenu(R.id.menuitem_program);
			// listView.getItemAtPosition(position)
			final List<NXTInstruction> rowItems = new ArrayList<NXTInstruction>();
			
			listView = (ListView) findViewById(R.id.commands_list);
			listView.setOnItemClickListener(new OnItemClickListener() {

				public void onItemClick(AdapterView<?> adapter, View v,
						int position, long id) {

					Log.i("NXTtt", "selected item : " + "la posicion es "
							+ position + "y el size es " + rowItems.size());
					
					rowItems.remove(position);
					listView = (ListView) findViewById(R.id.commands_list);
					CustomListViewAdapter adapter2 = funcaux(rowItems);
					listView.setAdapter(adapter2);

					// Log.i("NXTtt", "selected item : "
					// +" "+listView.getItemAtPosition(2)+" "+position);
				}
			});

			ImageView buttonUp = (ImageView) findViewById(R.id.button_up);

			buttonUp.setOnTouchListener(new CustomProgramButtonOnTouchListener(
					(ArrayList<NXTInstruction>) rowItems, new NXTInstruction(
							R.drawable.up_arrow, 1, "adelante"), this));

			ImageView buttonLeft = (ImageView) findViewById(R.id.button_left);
			buttonLeft
					.setOnTouchListener(new CustomProgramButtonOnTouchListener(
							(ArrayList<NXTInstruction>) rowItems,
							new NXTInstruction(R.drawable.left_arrow, 2,
									"izquierda"), this));

			ImageView buttonDown = (ImageView) findViewById(R.id.button_down);
			buttonDown
					.setOnTouchListener(new CustomProgramButtonOnTouchListener(
							(ArrayList<NXTInstruction>) rowItems,
							new NXTInstruction(R.drawable.down_arrow, 3,
									"atras"), this));

			ImageView buttonRight = (ImageView) findViewById(R.id.button_right);
			buttonRight
					.setOnTouchListener(new CustomProgramButtonOnTouchListener(
							(ArrayList<NXTInstruction>) rowItems,
							new NXTInstruction(R.drawable.right_arrow, 4,
									"derecha"), this));

			ImageView buttonPencil = (ImageView) findViewById(R.id.button_pencil);
			buttonPencil
					.setOnTouchListener(new CustomProgramButtonOnTouchListener(
							(ArrayList<NXTInstruction>) rowItems,
							new NXTInstruction(R.drawable.pencil, 6, "activar"), this));

			ImageView buttonNoPencil = (ImageView) findViewById(R.id.button_no_pencil);
			buttonNoPencil
					.setOnTouchListener(new CustomProgramButtonOnTouchListener(
							(ArrayList<NXTInstruction>) rowItems,
							new NXTInstruction(R.drawable.no_pencil, 5,
									"desactivar"), this));

			ImageView buttontLoop = (ImageView) findViewById(R.id.button_loop);
			buttontLoop
					.setOnTouchListener(new CustomProgramButtonOnTouchListener(
							(ArrayList<NXTInstruction>) rowItems,
							new NXTInstruction(R.drawable.loop_start_btn, 7, "ciclo", 2), this));

			ImageView buttontEndLoop = (ImageView) findViewById(R.id.button_end_loop);
			buttontEndLoop
					.setOnTouchListener(new CustomProgramButtonOnTouchListener(
							(ArrayList<NXTInstruction>) rowItems,
							new NXTInstruction(R.drawable.loop_end_btn, 8,
									"finCiclo"), this));
			
			Button clearButton = (Button) findViewById(R.id.button_clear);

			clearButton.setOnClickListener(new ClearList(rowItems));

			Button runButton = (Button) findViewById(R.id.button_run);
			Log.i("NXT", "fuerza : " + Integer.toString(mPower));
			mPower = 33;
			runButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					
					if (!isSyntaxCorrect())  return;

					int time = 0;
					int time1 = 1549;
					int time2 = 700;
					Stack<Integer> stackLoopsIndex = new Stack<Integer>();
					Stack<Integer> stackLoopCurrent = new Stack<Integer>();
					Log.i("tam rowItems", rowItems.size() + "");
					for (int i = 0; i < rowItems.size(); ++i) {
						Log.i("entre con", i + " ");
						switch (rowItems.get(i).duracion) {

						case 1:
							new Handler().postDelayed(new Runnable() {
								public void run() {
									mNXTTalker.moveFront(mPower, mReverse,
											mReverseLR);
								}
							}, time);
							time += time1;
							break;
						case 2:

							if (pencilDown) {
								new Handler().postDelayed(new Runnable() {
									public void run() {
										mNXTTalker.pencilUp();
									}
								}, time);
							}
							time += time2;

							new Handler().postDelayed(new Runnable() {
								public void run() {
									mNXTTalker.moveLeft(mPower, mReverse,
											mReverseLR);
								}
							}, time);
							time += time1;

							if (pencilDown) {
								new Handler().postDelayed(new Runnable() {
									public void run() {
										mNXTTalker.pencilDown();
									}
								}, time);
								time += time2;
							}

							break;
						case 3:
							new Handler().postDelayed(new Runnable() {
								public void run() {
									mNXTTalker.moveBack(mPower, mReverse,
											mReverseLR);
								}
							}, time);
							time += time1;
							break;
						case 4:
							if (pencilDown) {
								new Handler().postDelayed(new Runnable() {
									public void run() {
										mNXTTalker.pencilUp();
									}
								}, time);
							}
							time += time2;
							new Handler().postDelayed(new Runnable() {
								public void run() {
									mNXTTalker.moveRight(mPower, mReverse,
											mReverseLR);
								}
							}, time);
							time += time1;
							if (pencilDown) {
								new Handler().postDelayed(new Runnable() {
									public void run() {
										mNXTTalker.pencilDown();
									}
								}, time);
								time += time2;
							}
							break;
						case 5:// bajar
							pencilDown = true;
							new Handler().postDelayed(new Runnable() {
								public void run() {
									mNXTTalker.pencilDown();
								}
							}, time);
							time += time2;
							break;
						case 6: // levantar
							pencilDown = false;
							new Handler().postDelayed(new Runnable() {
								public void run() {
									mNXTTalker.pencilUp();
								}
							}, time);
							time += time2;
							break;
						case 7: // ciclo

							Log.i("Numero de repeticiones",
									(rowItems.get(i).repeticiones) + "");
							stackLoopCurrent.add(rowItems.get(i).repeticiones);
							stackLoopsIndex.add(i);
							Log.i("entre", " al ciclo ");
							break;
						case 8: // fin ciclo
							if (stackLoopCurrent.empty()) {

							} else if (stackLoopCurrent.peek() == 1) {
								stackLoopCurrent.pop();
								stackLoopsIndex.pop();
							} else {
								stackLoopCurrent.add(stackLoopCurrent.pop() - 1);
								i = stackLoopsIndex.peek();
							}
							break;

						}

					}
					new Handler().postDelayed(new Runnable() {
						public void run() {
							mNXTTalker.stopMovement();
						}
					}, time);

				}

				/**
				 * verifying that the syntax is correct
				 * @return
				 */
				private boolean isSyntaxCorrect() {
					
					int stack = 0;
					for (int i = 0; i < rowItems.size(); ++i) {
						Log.i("entre con", i + " ");
						if(rowItems.get(i).duracion == 7){
							stack++;
						}else if(rowItems.get(i).duracion == 8){
							stack--;
						}
						Log.i("stack va en ", stack+"");
						if(stack <  0) return false;
					}
					Log.i("stack quedo en ", stack+"");
					if(stack > 0) return false;
					
					return true;	
				}
				
				
			});

		} else if (mControlsMode == MODE_BUTTONS) {
			setContentView(R.layout.main);

			updateMenu(R.id.menuitem_buttons);

			ImageButton buttonUp = (ImageButton) findViewById(R.id.button_up);
			buttonUp.setOnTouchListener(new DirectionButtonOnTouchListener(1, 1));
			ImageButton buttonLeft = (ImageButton) findViewById(R.id.button_left);
			buttonLeft.setOnTouchListener(new DirectionButtonOnTouchListener(
					-0.6, 0.6));
			ImageButton buttonDown = (ImageButton) findViewById(R.id.button_down);
			buttonDown.setOnTouchListener(new DirectionButtonOnTouchListener(
					-1, -1));
			ImageButton buttonRight = (ImageButton) findViewById(R.id.button_right);
			buttonRight.setOnTouchListener(new DirectionButtonOnTouchListener(
					0.6, -0.6));

			SeekBar powerSeekBar = (SeekBar) findViewById(R.id.power_seekbar);
			powerSeekBar.setProgress(mPower);
			powerSeekBar
					.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
						@Override
						public void onProgressChanged(SeekBar seekBar,
								int progress, boolean fromUser) {
							mPower = progress;
						}

						@Override
						public void onStartTrackingTouch(SeekBar seekBar) {
						}

						@Override
						public void onStopTrackingTouch(SeekBar seekBar) {
						}
					});
		} else if (mControlsMode == MODE_TOUCHPAD) {
			setContentView(R.layout.main_touchpad);

			updateMenu(R.id.menuitem_touchpad);

			mTouchPadView = (TouchPadView) findViewById(R.id.touchpad);
			mTouchPadView.setOnTouchListener(new TouchpadOnTouchListener());
		} else if (mControlsMode == MODE_TANK) {
			setContentView(R.layout.main_tank);

			updateMenu(R.id.menuitem_tank);

			mTankView = (TankView) findViewById(R.id.tank);

			mTankView.setOnTouchListener(new TankOnTouchListener());
		} else if (mControlsMode == MODE_TANK3MOTOR) {
			setContentView(R.layout.main_tank3motor);

			updateMenu(R.id.menuitem_tank3motor);

			mTank3MotorView = (Tank3MotorView) findViewById(R.id.tank3motor);

			mTank3MotorView.setOnTouchListener(new Tank3MotorOnTouchListener());
		}

		mStateDisplay = (TextView) findViewById(R.id.state_display);

		mConnectButton = (Button) findViewById(R.id.connect_button);
		mConnectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!NO_BT) {
					findBrick();
				} else {
					mState = NXTTalker.STATE_CONNECTED;
					displayState();
				}
			}
		});

		mDisconnectButton = (Button) findViewById(R.id.disconnect_button);
		mDisconnectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mNXTTalker.stop();
			}
		});

		/*
		 * CheckBox reverseCheckBox = (CheckBox)
		 * findViewById(R.id.reverse_checkbox);
		 * reverseCheckBox.setChecked(mReverse);
		 * reverseCheckBox.setOnCheckedChangeListener(new
		 * OnCheckedChangeListener() {
		 * 
		 * @Override public void onCheckedChanged(CompoundButton buttonView,
		 * boolean isChecked) { mReverse = isChecked; } });
		 */

		displayState();
	}

	@Override
	protected void onStart() {
		super.onStart();
		// Log.i("NXT", "NXTRemoteControl.onStart()");
		if (!NO_BT) {
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			} else {
				if (mSavedState == NXTTalker.STATE_CONNECTED) {
					BluetoothDevice device = mBluetoothAdapter
							.getRemoteDevice(mDeviceAddress);
					mNXTTalker.connect(device);
				} else {
					if (mNewLaunch) {
						mNewLaunch = false;
						findBrick();
					}
				}
			}
		}
	}

	private void findBrick() {
		Intent intent = new Intent(this, ChooseDeviceActivity.class);
		startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			if (resultCode == Activity.RESULT_OK) {
				findBrick();
			} else {
				Toast.makeText(this, "Bluetooth not enabled, exiting.",
						Toast.LENGTH_LONG).show();
				finish();
			}
			break;
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == Activity.RESULT_OK) {
				String address = data.getExtras().getString(
						ChooseDeviceActivity.EXTRA_DEVICE_ADDRESS);
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
				// Toast.makeText(this, address, Toast.LENGTH_LONG).show();
				mDeviceAddress = address;
				mNXTTalker.connect(device);
			}
			break;
		case REQUEST_SETTINGS:
			// XXX?
			break;
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Log.i("NXT", "NXTRemoteControl.onSaveInstanceState()");
		if (mState == NXTTalker.STATE_CONNECTED) {
			outState.putString("device_address", mDeviceAddress);
		}
		// outState.putBoolean("reverse", mReverse);
		outState.putInt("power", mPower);
		outState.putInt("controls_mode", mControlsMode);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Log.i("NXT", "NXTRemoteControl.onConfigurationChanged()");
		setupUI();
	}

	private void displayState() {
		String stateText = null;
		int color = 0;
		switch (mState) {
		case NXTTalker.STATE_NONE:
			stateText = "Not connected";
			color = 0xffff0000;
			mConnectButton.setVisibility(View.VISIBLE);
			mDisconnectButton.setVisibility(View.GONE);
			setProgressBarIndeterminateVisibility(false);
			if (mWakeLock.isHeld()) {
				mWakeLock.release();
			}
			break;
		case NXTTalker.STATE_CONNECTING:
			stateText = "Connecting...";
			color = 0xffffff00;
			mConnectButton.setVisibility(View.GONE);
			mDisconnectButton.setVisibility(View.GONE);
			setProgressBarIndeterminateVisibility(true);
			if (!mWakeLock.isHeld()) {
				mWakeLock.acquire();
			}
			break;
		case NXTTalker.STATE_CONNECTED:
			stateText = "Connected";
			color = 0xff00ff00;
			mConnectButton.setVisibility(View.GONE);
			mDisconnectButton.setVisibility(View.VISIBLE);
			setProgressBarIndeterminateVisibility(false);
			if (!mWakeLock.isHeld()) {
				mWakeLock.acquire();
			}
			break;
		}
		mStateDisplay.setText(stateText);
		mStateDisplay.setTextColor(color);
	}

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			case MESSAGE_STATE_CHANGE:
				mState = msg.arg1;
				displayState();
				break;
			}
		}
	};

	@Override
	protected void onStop() {
		super.onStop();
		// Log.i("NXT", "NXTRemoteControl.onStop()");
		mSavedState = mState;
		mNXTTalker.stop();
		if (mWakeLock.isHeld()) {
			mWakeLock.release();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		mMenu = menu;
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuitem_buttons:
			mControlsMode = MODE_BUTTONS;
			setupUI();
			break;
		case R.id.menuitem_touchpad:
			mControlsMode = MODE_TOUCHPAD;
			setupUI();
			break;
		case R.id.menuitem_tank:
			mControlsMode = MODE_TANK;
			setupUI();
			break;
		case R.id.menuitem_tank3motor:
			mControlsMode = MODE_TANK3MOTOR;
			setupUI();
			break;
		case R.id.menuitem_program:
			mControlsMode = MODE_PROGRAM;
			setupUI();
			break;
		case R.id.menuitem_settings:
			Intent i = new Intent(this, SettingsActivity.class);
			startActivityForResult(i, REQUEST_SETTINGS);
			break;
		default:
			return false;
		}
		return true;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		readPreferences(sharedPreferences, key);
	}

	private void readPreferences(SharedPreferences prefs, String key) {
		if (key == null) {
			mReverse = prefs.getBoolean("PREF_SWAP_FWDREV", false);
			mReverseLR = prefs.getBoolean("PREF_SWAP_LEFTRIGHT", false);
			mRegulateSpeed = prefs.getBoolean("PREF_REG_SPEED", false);
			mSynchronizeMotors = prefs.getBoolean("PREF_REG_SYNC", false);
			if (!mRegulateSpeed) {
				mSynchronizeMotors = false;
			}
		} else if (key.equals("PREF_SWAP_FWDREV")) {
			mReverse = prefs.getBoolean("PREF_SWAP_FWDREV", false);
		} else if (key.equals("PREF_SWAP_LEFTRIGHT")) {
			mReverseLR = prefs.getBoolean("PREF_SWAP_LEFTRIGHT", false);
		} else if (key.equals("PREF_REG_SPEED")) {
			mRegulateSpeed = prefs.getBoolean("PREF_REG_SPEED", false);
			if (!mRegulateSpeed) {
				mSynchronizeMotors = false;
			}
		} else if (key.equals("PREF_REG_SYNC")) {
			mSynchronizeMotors = prefs.getBoolean("PREF_REG_SYNC", false);
		}
	}
}
