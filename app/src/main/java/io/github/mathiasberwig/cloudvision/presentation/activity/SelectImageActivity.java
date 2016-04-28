package io.github.mathiasberwig.cloudvision.presentation.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.gordonwong.materialsheetfab.MaterialSheetFab;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.mathiasberwig.cloudvision.R;
import io.github.mathiasberwig.cloudvision.controller.PermissionUtils;
import io.github.mathiasberwig.cloudvision.controller.service.CloudVisionUploader;
import io.github.mathiasberwig.cloudvision.controller.service.RestApisConsumer;
import io.github.mathiasberwig.cloudvision.presentation.custom_view.FAB;
import io.github.mathiasberwig.cloudvision.presentation.fragment.LoadingFragment;
import io.github.mathiasberwig.cloudvision.presentation.fragment.SelectImageFragment;

public class SelectImageActivity extends AppCompatActivity {
    private static final String TAG = SelectImageActivity.class.getName();

    private static final int GALLERY_IMAGE_REQUEST = 1;
    private static final int CAMERA_IMAGE_REQUEST = 2;
    private static final int CAMERA_PERMISSIONS_REQUEST = 3;
    private static final int GALLERY_PERMISSIONS_REQUEST = 4;

    // UI Components
    private MaterialSheetFab materialSheetFab;
    private MenuItem menuSettings;

    /**
     * Receiver that is executed when the {@link CloudVisionUploader} finishes sending the image to
     * server. In case of any errors, it disable loading animation and shows a Toast to the user.
     * If the image was sent successfully, it starts the {@link RestApisConsumer} to query third-party
     * servers info about the image.
     */
    private BroadcastReceiver uploadCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Valid response from Google Cloud Vision
            if (intent.hasExtra(CloudVisionUploader.EXTRA_RESULT_LABELS)
             || intent.hasExtra(CloudVisionUploader.EXTRA_RESULT_LANDMARK)) {

                // Start the RestApisConsumer IntentService
                Intent service = new Intent(SelectImageActivity.this, RestApisConsumer.class);
                service.putExtras(intent.getExtras());
                startService(service);
            } else

            // Error uploading image or retrieving data from Google Cloud Vision
            if (intent.hasExtra(CloudVisionUploader.EXTRA_RESULT_ERROR)) {
                // TODO: Replace Toast with a EmptyView showing the Error Message
                // Show Toast with the error
                Toast.makeText(SelectImageActivity.this,
                        intent.getStringExtra(CloudVisionUploader.EXTRA_RESULT_ERROR),
                        Toast.LENGTH_LONG).show();

                // Disable loading animation
                toggleLoading(false);
            } else

            // CloudVision haven't returned enough data
            {
                // Disable loading animation
                toggleLoading(false);

                // TODO: Replace Toast with a EmptyView showing that nothing was found
                // Show Toast with the error
                Toast.makeText(SelectImageActivity.this,
                        "Empty View",
                        Toast.LENGTH_LONG).show();
            }
        }
    };

    /**
     * Receiver that is executed when the {@link RestApisConsumer} finishes querying data from
     * third-party services about the image sent by {@link CloudVisionUploader}. It stops the loading
     * animation and starts {@link MainActivity}.
     */
    private BroadcastReceiver queryCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Start MainActivity with the info returned from Google Cloud Vision and third-party APIs
            MainActivity.newInstance(SelectImageActivity.this, intent.getExtras());

            // Disable loading animation
            toggleLoading(false);
        }
    };

    /**
     * The filename of the image selected by the user. It's stored on the default application folder.
     */
    public static final String FILE_NAME = "cloud_vision.jpg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_image);

        setupToolbar();
        setupFAB();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_select_image, menu);

        // Get the Action Settings menu item, so we can show/hide it after
        menuSettings = menu.findItem(R.id.action_settings);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (materialSheetFab.isSheetVisible()) {
            materialSheetFab.hideSheet();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSIONS_REQUEST) {
            if (PermissionUtils.permissionGranted(requestCode, CAMERA_PERMISSIONS_REQUEST, grantResults)) {
                startCamera(null);
            }
        } else if (requestCode == GALLERY_PERMISSIONS_REQUEST) {
            if (PermissionUtils.permissionGranted(requestCode, GALLERY_PERMISSIONS_REQUEST, grantResults)) {
                startGalleryChooser(null);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Gets the image chosen by the user and start uploading it to the server
        if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            try {
                CloudVisionUploader.start(this, copyFileFromGallery(data.getData()));
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            toggleLoading(true);
        }
        else if(requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            CloudVisionUploader.start(this, Uri.fromFile(getCameraFile()));
            toggleLoading(true);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(uploadCompleteReceiver, new IntentFilter(CloudVisionUploader.ACTION_DONE));
        lbm.registerReceiver(queryCompleteReceiver, new IntentFilter(RestApisConsumer.ACTION_DONE));
    }

    @Override
    protected void onStop() {
        super.onStop();

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(uploadCompleteReceiver);
        lbm.unregisterReceiver(queryCompleteReceiver);
    }

    /**
     * Gets the {@link Toolbar} view and sets the {@code ActionBar}.
     */
    private void setupToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    /**
     * Gets the {@link R.id#fab}, {@link R.id#fab_sheet} and {@link R.id#dim_overlay}. Initializes
     * and set event listener for {@link #materialSheetFab}.
     */
    private void setupFAB() {
        FAB fab = (FAB) findViewById(R.id.fab);
        View sheetView = findViewById(R.id.fab_sheet);
        View overlay = findViewById(R.id.dim_overlay);
        int sheetColor = getResources().getColor(R.color.cardview_light_background);
        int fabColor = getResources().getColor(R.color.colorAccent);
        assert fab != null && sheetView != null && overlay != null;

        // Initialize material sheet FAB
        materialSheetFab = new MaterialSheetFab<>(fab, sheetView, overlay, sheetColor, fabColor);
    }

    /**
     * Toggles visibility of FAB and replaces the current fragment.
     *
     * @param isLoading {@code true} to hide the FAB and load the {@link LoadingFragment};
     * {@code false} to show FAB and load the {@link SelectImageFragment}.
     */
    private void toggleLoading(boolean isLoading) {

        // Create the transaction to replace fragment
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Toggle visibility of system status bar and FAB then replaces the fragment
        if (isLoading) {
            materialSheetFab.hideSheetThenFab();

            LoadingFragment loadingFragment = LoadingFragment.newInstance();
            transaction.replace(R.id.fragment_container, loadingFragment);
        } else {
            materialSheetFab.showFab();

            SelectImageFragment selectImageFragment = SelectImageFragment.newInstance();
            transaction.replace(R.id.fragment_container, selectImageFragment);
        }

        // Toggle visibility of Settings menu item
        menuSettings.setVisible(!isLoading);

        transaction.commit();
    }

    /**
     * Creates an intent with {@link Intent#ACTION_GET_CONTENT} to select a photo. Starts the intent
     * with {@code startActivityForResult}.
     *
     * @param view Mandatory to use with {@code onClick} event.
     */
    public void startGalleryChooser(View view) {
        if (PermissionUtils.requestPermission(this, GALLERY_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select a photo"), GALLERY_IMAGE_REQUEST);
        }
    }

    /**
     * Request permission to use camera and creates an intent with {@link MediaStore#ACTION_IMAGE_CAPTURE}
     * to take a photo. Starts the intent with {@code startActivityForResult}.
     *
     * @param view Mandatory to use with {@code onClick} event.
     */
    public void startCamera(View view) {
        if (PermissionUtils.requestPermission(this, CAMERA_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(getCameraFile()));
            startActivityForResult(intent, CAMERA_IMAGE_REQUEST);
        }
    }

    /**
     * Gets a new file to store the image taken with {@link #startCamera(View)} method.
     *
     * @return new file on {@link Environment#DIRECTORY_PICTURES} with {@link #FILE_NAME}.
     */
    public File getCameraFile() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(dir, FILE_NAME);
    }

    private Uri copyFileFromGallery(Uri uri) throws IOException {

        InputStream inputStream = null;
        OutputStream outputStream = null;
        File outputFile = getCameraFile();

        try {
            // read this file into InputStream
            inputStream = getContentResolver().openInputStream(uri);

            // write the inputStream to a FileOutputStream
            outputStream = new FileOutputStream(outputFile);

            int read = 0;
            byte[] bytes = new byte[1024];

            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
            return Uri.fromFile(outputFile);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
            return uri;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    // outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }
}
