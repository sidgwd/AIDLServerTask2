package com.jio.server.aidl.service;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.text.format.DateFormat;

import androidx.annotation.Nullable;

import com.jio.server.sdk.ICallback;
import com.jio.server.sdk.ISdkOrientationProvider;
import com.jio.server.sdk.OrientationModel;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ISdkCallerService extends Service {

    private RemoteCallbackList<ICallback> icallbacks;
    ISdkOrientationProvider.Stub mBinder = new ISdkOrientationProvider.Stub() {

        @Override
        public void getOrientationLogs(String deviceId) throws RemoteException {


            SensorManager mSensorManager = (SensorManager) ISdkCallerService.this.getSystemService(Activity.SENSOR_SERVICE);
            Sensor mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            mSensorManager.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent sensorEvent) {
                    OrientationModel orientationModel = new OrientationModel();
                    orientationModel.setCurrentOrientation(String.valueOf("\nazimuth/Yaw: " +(float) Math.toDegrees( sensorEvent.values[0]))
                            +"\nPitch: " +(float) Math.toDegrees( sensorEvent.values[1])
                            +"\nRoll: " +(float) Math.toDegrees( sensorEvent.values[2]));
                    String status = "NO_CONTACT";
                    switch (sensorEvent.accuracy) {
                        case SensorManager.SENSOR_STATUS_NO_CONTACT:
                            status = "NO_CONTACT";
                            break;
                        case SensorManager.SENSOR_STATUS_UNRELIABLE:
                            status = "UNRELIABLE";
                            break;
                        case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                            status = "ACCURACY_LOW";
                            break;
                        case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                            status = "ACCURACY_MEDIUM";
                            break;
                        case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                            status = "ACCURACY_HIGH";
                            break;
                    }

                    orientationModel.setAccuracy(String.valueOf(status));

                    long timeInMillis = (new Date()).getTime()
                            + (sensorEvent.timestamp - System.nanoTime()) / 1000000L;
                    orientationModel.setTimeStamp(String.valueOf(getDate(timeInMillis) ));
                    orientationModel.setMaxEventsCount(String.valueOf(sensorEvent.sensor.getFifoMaxEventCount()));
                    orientationModel.setReserverdEventsCount(String.valueOf(sensorEvent.sensor.getFifoReservedEventCount()));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        orientationModel.setHighestDirectReportRateLevel(String.valueOf(sensorEvent.sensor.getHighestDirectReportRateLevel()));
                    }


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        orientationModel.setId(String.valueOf(sensorEvent.sensor.getId()));
                    }
                    orientationModel.setMaxDelay(String.valueOf(sensorEvent.sensor.getMaxDelay()));
                    orientationModel.setMaxRange(String.valueOf(sensorEvent.sensor.getMaximumRange()));
                    orientationModel.setMinDelay(String.valueOf(sensorEvent.sensor.getMinDelay()));
                    orientationModel.setName(sensorEvent.sensor.getName());
                    orientationModel.setPower(String.valueOf(sensorEvent.sensor.getPower()));
                    orientationModel.setReportingMode(String.valueOf(sensorEvent.sensor.getReportingMode()));
                    orientationModel.setResolution(String.valueOf(sensorEvent.sensor.getResolution()));
                    orientationModel.setStringType(sensorEvent.sensor.getStringType());
                    orientationModel.setType(String.valueOf(sensorEvent.sensor.getType()));
                    orientationModel.setVendor(sensorEvent.sensor.getVendor());
                    orientationModel.setVersion(String.valueOf(sensorEvent.sensor.getVersion()));

                    // client-side callback in a broadcast
                    int len = icallbacks.beginBroadcast();
                    for (int i = 0; i < len; i++) {
                        try {
                            icallbacks.getBroadcastItem(i).onResult(orientationModel);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    // Remember to turn off the radio
                    icallbacks.finishBroadcast();
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int i) {

                }
            }, mRotationVectorSensor, 8);


        }

        @Override
        public void registerCallback(ICallback cb) throws RemoteException {
            if (cb != null) {
                icallbacks.register(cb);
            }

        }

        @Override
        public void unregisterCallback(ICallback cb) throws RemoteException {
            if (cb != null) {
                icallbacks.unregister(cb);
            }

        }
    };

    private String getDate(long time) {
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(time );
        String date = DateFormat.format("dd-MM-yyyy HH:mm:ss", cal).toString();
        return date;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        icallbacks = new RemoteCallbackList<>();

        return mBinder;
    }


    private void calcOrientation(float[] orientation, float[] incomingValues) {
        // Get the quaternion
        float[] quatF = new float[4];
        SensorManager.getQuaternionFromVector(quatF, incomingValues);

        // Get the rotation matrix
        //
        // This is a variant on the code presented in
        // http://www.euclideanspace.com/maths/geometry/rotations/conversions/quaternionToMatrix/
        // which has been altered for scaling and (I think) a different axis arrangement. It
        // tells you the rotation required to get from the between the phone's axis
        // system and the earth's.
        //
        // Phone axis system:
        // https://developer.android.com/guide/topics/sensors/sensors_overview.html#sensors-coords
        //
        // Earth axis system:
        // https://developer.android.com/reference/android/hardware/SensorManager.html#getRotationMatrix(float[], float[], float[], float[])
        //
        // Background information:
        // https://en.wikipedia.org/wiki/Rotation_matrix
        //
        float[][] rotMatF = new float[3][3];
        rotMatF[0][0] = quatF[1]*quatF[1] + quatF[0]*quatF[0] - 0.5f;
        rotMatF[0][1] = quatF[1]*quatF[2] - quatF[3]*quatF[0];
        rotMatF[0][2] = quatF[1]*quatF[3] + quatF[2]*quatF[0];
        rotMatF[1][0] = quatF[1]*quatF[2] + quatF[3]*quatF[0];
        rotMatF[1][1] = quatF[2]*quatF[2] + quatF[0]*quatF[0] - 0.5f;
        rotMatF[1][2] = quatF[2]*quatF[3] - quatF[1]*quatF[0];
        rotMatF[2][0] = quatF[1]*quatF[3] - quatF[2]*quatF[0];
        rotMatF[2][1] = quatF[2]*quatF[3] + quatF[1]*quatF[0];
        rotMatF[2][2] = quatF[3]*quatF[3] + quatF[0]*quatF[0] - 0.5f;

        // Get the orientation of the phone from the rotation matrix
        //
        // There is some discussion of this at
        // http://stackoverflow.com/questions/30279065/how-to-get-the-euler-angles-from-the-rotation-vector-sensor-type-rotation-vecto
        // in particular equation 451.
        //
        final float rad2deg = (float)(180.0 / 3.14);
        orientation[0] = (float)Math.atan2(-rotMatF[1][0], rotMatF[0][0]) * rad2deg;
        orientation[1] = (float)Math.atan2(-rotMatF[2][1], rotMatF[2][2]) * rad2deg;
        orientation[2] = (float)Math.asin ( rotMatF[2][0])                * rad2deg;
        if (orientation[0] < 0) orientation[0] += 360;
    }


}
