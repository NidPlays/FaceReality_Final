
package com.miniproject.facereality.augmentedfaces;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.AugmentedFace.RegionType;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Config.AugmentedFaceMode;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.augmentedfaces.AugmentedFaceRenderer;
import com.google.ar.core.examples.java.augmentedfaces.R;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class AugmentedFacesActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String CHANNEL_ID = "notifchannel";
  Button btnScreenshot;

  private static final String TAG = AugmentedFacesActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
  private final AugmentedFaceRenderer augmentedFaceRenderer = new AugmentedFaceRenderer();
  private final ObjectRenderer noseObject = new ObjectRenderer();
  private final ObjectRenderer rightEarObject = new ObjectRenderer();
  private final ObjectRenderer leftEarObject = new ObjectRenderer();
  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] noseMatrix = new float[16];
  private final float[] rightEarMatrix = new float[16];
  private final float[] leftEarMatrix = new float[16];
  private static final float[] DEFAULT_COLOR = new float[] {0f, 0f, 0f, 0f};

  protected boolean printOptionEnable = false;

  int width_surface , height_surface ;

  private static final int REQUEST_EXTERNAL_STORAGE = 1;
  private static final String[] PERMISSION_STORAGE = {
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE,
          Manifest.permission.CAMERA,
  };



  public static void verifyStoragePermission(Activity activity) {
    int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

    if (permission != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(
              activity,
              PERMISSION_STORAGE,
              REQUEST_EXTERNAL_STORAGE);
    }
  }

  private void createNotificationChannel() {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      CharSequence name = getString(R.string.channel_name);
      String description = getString(R.string.channel_description);
      int importance = NotificationManager.IMPORTANCE_DEFAULT;
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
      channel.setDescription(description);
      // Register the channel with the system; you can't change the importance
      // or other notification behaviors after this
      NotificationManager notificationManager = getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(channel);
    }
  }

  //random int gen
  public int gen() {
    Random r = new Random( System.currentTimeMillis() );
    return ((1 + r.nextInt(2)) * 10000 + r.nextInt(10000));
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    verifyStoragePermission(this);
    createNotificationChannel();
    surfaceView = findViewById(R.id.surfaceview);
    btnScreenshot = findViewById(R.id.button_screenshot);
    final MediaPlayer mp =MediaPlayer.create(this,R.raw.shutter);
    btnScreenshot.setOnClickListener(view -> {
      Log.v("nid", "pan button clicked");
      //used to make toast
      Toast.makeText(getBaseContext(),"Image taken",Toast.LENGTH_SHORT).show();
      //used to take screenshot
      printOptionEnable = true;
      //used to play the shutter audio
      mp.start();
      
      


    });
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    surfaceView.setWillNotDraw(false);

    installRequested = false;
  }

  @Override
  protected void onDestroy() {
    if (session != null) {
      // Explicitly close ARCore Session to release native resources.
      // Review the API reference for important considerations before calling close() in apps with
      // more complicated lifecycle requirements:
      // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
      session.close();
      session = null;
    }

    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session and configure it to use a front-facing (selfie) camera.
        session = new Session(/* context= */ this, EnumSet.noneOf(Session.Feature.class));
        CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session);
        cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT);
        List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
        if (!cameraConfigs.isEmpty()) {
          // Element 0 contains the camera config that best matches the session feature
          // and filter settings.
          session.setCameraConfig(cameraConfigs.get(0));
        } else {
          message = "This device does not have a front-facing (selfie) camera";
          exception = new UnavailableDeviceNotCompatibleException(message);
        }
        configureSession();

      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    try {
      // Create the texture and pass it to ARCore session to be filled during update().
      backgroundRenderer.createOnGlThread(/*context=*/ this);
      augmentedFaceRenderer.createOnGlThread(this, "models/freckles.png");
      augmentedFaceRenderer.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      noseObject.createOnGlThread(/*context=*/ this, "models/nose.obj", "models/nose_fur.png");
      noseObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      noseObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
      rightEarObject.createOnGlThread(this, "models/forehead_right.obj", "models/ear_fur.png");
      rightEarObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      rightEarObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
      leftEarObject.createOnGlThread(this, "models/forehead_left.obj", "models/ear_fur.png");
      leftEarObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      leftEarObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);

    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
    width_surface =  width ;
    height_surface = height ;
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    try {
      session.setCameraTextureName(backgroundRenderer.getTextureId());

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();

      // Get projection matrix.
      float[] projectionMatrix = new float[16];
      camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

      // Get camera matrix and draw.
      float[] viewMatrix = new float[16];
      camera.getViewMatrix(viewMatrix, 0);

      // Compute lighting from average intensity of the image.
      // The first three components are color scaling factors.
      // The last one is the average pixel intensity in gamma space.
      final float[] colorCorrectionRgba = new float[4];
      frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

      // If frame is ready, render camera preview image to the GL surface.
      backgroundRenderer.draw(frame);

      // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
      trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

      // ARCore's face detection works best on upright faces, relative to gravity.
      // If the device cannot determine a screen side aligned with gravity, face
      // detection may not work optimally.
      Collection<AugmentedFace> faces = session.getAllTrackables(AugmentedFace.class);
      for (AugmentedFace face : faces) {
        if (face.getTrackingState() != TrackingState.TRACKING) {
          break;
        }


        float scaleFactor = 1.0f;

        // Face objects use transparency so they must be rendered back to front without depth write.
        GLES20.glDepthMask(false);

        // Each face's region poses, mesh vertices, and mesh normals are updated every frame.

        // 1. Render the face mesh first, behind any 3D objects attached to the face regions.
        float[] modelMatrix = new float[16];
        face.getCenterPose().toMatrix(modelMatrix, 0);
        augmentedFaceRenderer.draw(
            projectionMatrix, viewMatrix, modelMatrix, colorCorrectionRgba, face);

        // 2. Next, render the 3D objects attached to the forehead.
        face.getRegionPose(RegionType.FOREHEAD_RIGHT).toMatrix(rightEarMatrix, 0);
        rightEarObject.updateModelMatrix(rightEarMatrix, scaleFactor);
        rightEarObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);

        face.getRegionPose(RegionType.FOREHEAD_LEFT).toMatrix(leftEarMatrix, 0);
        leftEarObject.updateModelMatrix(leftEarMatrix, scaleFactor);
        leftEarObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);

        // 3. Render the nose last so that it is not occluded by face mesh or by 3D objects attached
        // to the forehead regions.
        face.getRegionPose(RegionType.NOSE_TIP).toMatrix(noseMatrix, 0);
        noseObject.updateModelMatrix(noseMatrix, scaleFactor);
        noseObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);
      }

    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    } finally {
      GLES20.glDepthMask(true);
    }


    try {
      if (printOptionEnable) {
        printOptionEnable = false ;
        Log.i("nid", "printOptionEnable if condition:" + printOptionEnable);
        int w = width_surface ;
        int h = height_surface  ;

        Log.i("nid", "w:"+w+"-----h:"+h);

        int[] b =new int[(int) (w*h)];
        int[] bt =new int[(int) (w*h)];
        IntBuffer buffer=IntBuffer.wrap(b);
        buffer.position(0);
        GLES20.glReadPixels(0, 0, w, h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE, buffer);
        for(int i=0; i<h; i++)
        {
          //remember, that OpenGL bitmap is incompatible with Android bitmap
          //and so, some correction need.
          for(int j=0; j<w; j++)
          {
            int pix=b[i*w+j];
            int pb=(pix>>16)&0xff;
            int pr=(pix<<16)&0x00ff0000;
            int pix1=(pix&0xff00ff00) | pr | pb;
            bt[(h-i-1)*w+j]=pix1;
          }
        }
        Bitmap inBitmap = null ;
        if (inBitmap == null || !inBitmap.isMutable()
                || inBitmap.getWidth() != w || inBitmap.getHeight() != h) {
          inBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        //Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        inBitmap.copyPixelsFromBuffer(buffer);
        inBitmap = Bitmap.createBitmap(bt, w, h, Bitmap.Config.ARGB_8888);

        final Calendar c=Calendar.getInstance();
        long mytimestamp=c.getTimeInMillis();
        String timeStamp=String.valueOf(mytimestamp);
        //String myfile="nid"+timeStamp+".jpeg";

        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
        String fileName = "FaceReality_"+timeStamp + ".jpg";
        File file = new File(directory, fileName);
        if (!file.exists()) {
          Log.d("nid", file.toString());
          FileOutputStream fos;
          try {
            fos = new FileOutputStream(file);
            inBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
            MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());
            Uri medUri = MediaStore.Images.Media.getContentUri(file.getName());
            //notification
            ///Intent intent = new Intent(this, AugmentedFacesActivity.class);
            //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);


            //file files = new File(file.getAbsolutePath()); // set your image path



            Log.d("nid",file.toString());

            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_VIEW);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            //intent.setDataAndType(Uri.fromFile(file), "image/*");

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("Image Saved Successfully")
                    .setContentText("Image saved in Pictures folder. File name starts with FaceReality_timestamp")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);
            int notificationId = gen();
            notificationManager.notify(notificationId, builder.build());



          } catch (java.io.IOException e) {
            e.printStackTrace();
          }
        }



      }
    } catch(Exception e) {
      e.printStackTrace();
    }
  }




  private void configureSession() {
    Config config = new Config(session);
    config.setAugmentedFaceMode(AugmentedFaceMode.MESH3D);
    session.configure(config);
  }
}
