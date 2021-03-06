package com.sdsmdg.skipthequeue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.kontakt.sdk.android.common.profile.IEddystoneDevice;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.table.TableOperationCallback;
import com.microsoft.windowsazure.mobileservices.table.TableQueryCallback;
import com.sdsmdg.skipthequeue.BeaconFinder.BeaconFinderService;
import com.sdsmdg.skipthequeue.models.Machine;
import com.sdsmdg.skipthequeue.models.Response;
import com.sdsmdg.skipthequeue.models.User;
import com.sdsmdg.skipthequeue.otp.MSGApi;
import com.victor.loading.rotate.RotateLoading;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import mehdi.sakout.fancybuttons.FancyButton;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class SignupActivity extends AppCompatActivity {

    private final static String TAG = SignupActivity.class.getSimpleName();

    ArrayList<IEddystoneDevice> beaconsArray;
    private IEddystoneDevice beacon;
    BroadcastReceiver broadcastReceiver;

    private MobileServiceClient mClient;
    private MobileServiceTable<User> table;
    EditText mobileEditText;
    private int maxqueueNo = 0;
    private TextView infoTextView;
    private TextView time ;
    private RotateLoading rotateLoading;
    private int lengthUsers;
    private FancyButton getToken;

    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        prefs = getDefaultSharedPreferences(this);

        //Removes shadow from under the action bar
        getSupportActionBar().setElevation(0);
        maxqueueNo = 0;
        rotateLoading = (RotateLoading)findViewById(R.id.rotateloading);
        infoTextView = (TextView)findViewById(R.id.infoTextView);
        mobileEditText = (EditText) findViewById(R.id.mobile_editText);
        beacon = (IEddystoneDevice) getIntent().getSerializableExtra(BeaconScannerActivity.BEACON);
        makeClient();
        makeReceiver();
        getToken = (FancyButton)findViewById(R.id.get_token_button);
    }

    private void makeClient() {

        try {
            mClient = new MobileServiceClient(
                    "https://skipthequeue.azurewebsites.net",
                    this
            );
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        table = mClient.getTable(Helper.machine.tableName, User.class);
    }

    private void makeReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                beaconsArray = intent.getParcelableArrayListExtra(BeaconFinderService.beacons_array);
                if(!beaconsArray.contains(beacon))
                {
                    Toast.makeText(SignupActivity.this, "Beacon Lost, please stay into proximity.", Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(SignupActivity.this, StartingActivity.class);
                    startActivity(i);
                }

            }
        };
    }

    @Override
    protected void onStart() {

        //Register the receiver
        LocalBroadcastManager.getInstance(this).registerReceiver((broadcastReceiver),
                new IntentFilter(BeaconFinderService.intent_filter)
        );

        super.onStart();
    }

    @Override
    protected void onStop() {

        //Unregister the receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        super.onStop();

    }

    public void signupClicked(View view) {

        //Generates a random no as clientId
        getToken.setEnabled(false);
        int clientId = (int) (1000 + Math.random() * 9000);
        generateQueueNo(String.valueOf(clientId));
        rotateLoading.start();
    }

    private void sendClientId(final User user) {
        OkHttpClient.Builder client = new OkHttpClient.Builder();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://control.msg91.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client.build())
                .build();

        MSGApi api = retrofit.create(MSGApi.class);

        Call<Response> call = api.sendOTP(
                Keys.MSG_KEY,
                user.mobile,
                generateMessage(user),
                "SKIPTQ",
                4,
                91,
                "json"
        );

        call.enqueue(new Callback<Response>() {
            @Override
            public void onResponse(Call<Response> call, retrofit2.Response<Response> response) {
                if (response.body().getType().equals("success")) {
                    insertEntry(user);
                    //save the atm table name in user preferences(For use in view status button on starting activity)
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("table_name", Helper.machine.tableName);
                    editor.apply();
                } else {
                    Toast.makeText(getApplicationContext(), "Problem with message API", Toast.LENGTH_LONG).show();
                    Log.i(TAG, response.body().getType());
                    Log.i(TAG, response.body().getMessage());
                }
            }

            @Override
            public void onFailure(Call<Response> call, Throwable t) {
                rotateLoading.stop();
            }
        });
    }

    private String generateMessage(User user) {

        String str = "Your Token Number is "+ user.clientId + ".\n" +
                "Your number is "+ user.queueNo +" in the queue. The expected waiting time is nearly "+ getApproxtime() + "\n"+
                "\n" +
                "[Note:This token can be used only once, so, use it when you reach to end of the queue] \n\n"+
                "Thanks for using Skip the Queue service, have a nice day.";
        return str;

    }

    private String getApproxtime() {

        int mins = lengthUsers*2;


         if(mins > 50 && mins < 70)
            return "one hour.";

         else if(mins > 110 && mins < 130)
             return "two hours.";
         else if(mins > 170 && mins < 190)
             return "two hours.";
         else if(mins > 230 && mins < 250)
             return "two hours.";
         else if(mins > 290 && mins < 310)
             return "two hours.";


         else
         {
             int hours = mins/60;
             mins = mins%60;

             if (hours > 0)
             {
                 return hours + " hours and " + mins + "minutes.";
             }

             else
                 return  mins + " minutes.";
         }
    }

    private void insertEntry(final User user) {
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params)
            {

                table.insert(user, new TableOperationCallback<User>() {
                    public void onCompleted(User entity, Exception exception, ServiceFilterResponse response) {
                        if (exception == null) {
                            // Insert succeeded
                            Toast.makeText(SignupActivity.this, "Token generated!", Toast.LENGTH_SHORT).show();
                            rotateLoading.stop();
                            infoTextView.setVisibility(View.VISIBLE);
                            changeQueueNoInManagerTable(1);
                            openLoginActivity();
                        } else {
                            // Insert failed
                            exception.printStackTrace();
                            rotateLoading.stop();
                            insertEntry(user);
                        }
                    }
                });

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {

            }
        };
        task.execute();
    }

    //Give the change that is to be made as signed argument.

    public void changeQueueNoInManagerTable(final int change) {
        MobileServiceTable<Machine> managerTable = mClient.getTable("Manager", Machine.class);
        Machine currentMachine = Helper.machine;
        currentMachine.queueLength += change;
        managerTable.update(currentMachine, new TableOperationCallback<Machine>() {
            @Override
            public void onCompleted(Machine entity, Exception exception, ServiceFilterResponse response) {
                if(exception != null) {
                    changeQueueNoInManagerTable(change);
                } else {
                    //Toast.makeText(SignupActivity.this, "Queue no. updated in manager table", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    //Generates the queue no. then send client id, and finally use that queue no. to enter data in database
    private void generateQueueNo(final String clientId) {

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    //Receive results from backend, only first 50 entries are received.
                    table.where().execute(new TableQueryCallback<User>() {
                        @Override
                        public void onCompleted(List<User> result, int count, Exception exception, ServiceFilterResponse response) {
                            if(exception == null)
                            {
                                lengthUsers = result.size();
                                for (User user : result) {
                                    if (user.queueNo > maxqueueNo) {
                                        maxqueueNo = user.queueNo;
                                    }
                                }
                                //This runs the supposed Post Execute method only when the call succeeds.
                                onUIthread(clientId);
                            }
                            else {
                                generateQueueNo(clientId);
                            }
                        }


                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                }

                return null;
            }

        };
        task.execute();
    }

    private void onUIthread(final String clientId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final User user = new User();
                //91 added to user's mobile no.
                user.mobile = "91"+ mobileEditText.getText().toString();
                user.clientId = clientId;
                user.queueNo = maxqueueNo + 1;

                if(user.mobile.length() != 12 )
                {
                    Toast.makeText(getApplicationContext(),"Please Enter a valid Mobile No.",Toast.LENGTH_SHORT).show();
                    rotateLoading.stop();
                    getToken.setEnabled(true);
                }
                else {
                    //TODO : Iterate through db and allow token generation only once for a particular mobile no.
                    sendClientId(user);
                }

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getToken.setEnabled(true);
    }

    //This function is called just after the signup is successful
    private void openLoginActivity() {
        Intent i = new Intent(this, MainActivity.class);
        startActivity(i);
    }
}
