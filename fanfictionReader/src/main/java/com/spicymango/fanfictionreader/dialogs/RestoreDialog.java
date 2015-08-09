package com.spicymango.fanfictionreader.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.slezica.tools.async.ManagedAsyncTask;
import com.slezica.tools.async.TaskManagerFragment;
import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.Settings;
import com.spicymango.fanfictionreader.util.FileHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class RestoreDialog extends DialogFragment {
    private ProgressBar bar;

    public static File findBackUpFile(Context context) {
        File emuStorageDir, extStorageDir, backUpFile;

        emuStorageDir = Environment.getExternalStorageDirectory();
        extStorageDir = FileHandler.getExternalStorageDirectory(context);

        backUpFile = null;

        if (FileHandler.isEmulatedFilesDirWriteable()) {
            backUpFile = new File(emuStorageDir, BackUpDialog.filename);
        }

        if ((backUpFile == null || !backUpFile.exists()) && FileHandler.isExternalStorageWritable(context)) {
            backUpFile = new File(extStorageDir, BackUpDialog.filename);
        }

        if (backUpFile == null || !backUpFile.exists()) {
            return null;
        }
        return backUpFile;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setCancelable(false);

        bar = new ProgressBar(getActivity(), null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setId(android.R.id.progress);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.diag_restoring);
        builder.setView(bar);

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();

        Fragment manager = getFragmentManager().findFragmentByTag(
                TaskManagerFragment.DEFAULT_TAG);
        if (manager == null) {
            new RestoreTask(getActivity()).execute((Void) null);
        }
    }

    private static class RestoreTask extends
            ManagedAsyncTask<Void, Integer, Integer> {
        private final File intFilesDir, emuFilesDir, extFilesDir;
        private final File zipFile, dataFile;
        private final boolean saveOnInternal;
        private Integer[] progress = {0, 0};

        public RestoreTask(FragmentActivity activity) {
            super(activity);

            // Set up directories
            intFilesDir = activity.getFilesDir();
            emuFilesDir = FileHandler.getEmulatedFilesDir(activity);
            extFilesDir = FileHandler.getExternalFilesDir(activity);

            saveOnInternal = !Settings.shouldWriteToSD(activity) || (extFilesDir == null);

            // Try to find the backup file
            File backUpFile = findBackUpFile(activity);
            if (backUpFile == null) {
                cancel(true);
            }

            zipFile = backUpFile;
            String s = activity.getApplicationInfo().dataDir;
            dataFile = new File(s);
        }

        /**
         * Deletes a directory and all of its contents
         *
         * @param dir The directory to delete
         * @return True if all the files are successfully deleted, false
         * otherwise
         */
        private static final boolean deleteDir(File dir) {
            boolean success = true;
            if (dir.isDirectory()) {
                String[] children = dir.list();
                for (int i = 0; i < children.length; i++) {
                    success &= deleteDir(new File(dir, children[i]));
                }
            }
            return success & dir.delete();
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int result = R.string.toast_restore_successful;

            ZipFile file = null;
            try {
                // Open the backUp file; throws an exception if an error occurs
                file = new ZipFile(zipFile);

                // Delete any pre-existing files, as they will be inaccessible
                // after the restore
                deleteDir(intFilesDir);

                if (emuFilesDir != null) {
                    deleteDir(emuFilesDir);
                }

                // Do not delete those on the SD card, as it may be shared
                // across several devices
                // deleteDir(extFilesDir);

                // Set the counter for the progress bar
                progress[1] = file.size();
//                publishProgress(progress);

                // Unzip all entries
                Enumeration<? extends ZipEntry> entries = file.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    extractEntry(entry, file.getInputStream(entry));
                }

            } catch (ZipException e) {
                result = R.string.error_corrupted;
            } catch (FileNotFoundException e) {
                result = R.string.error_backup_not_found;
            } catch (IOException e) {
                result = R.string.error_unknown;
            } finally {
                try {
                    if (file != null) {
                        file.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return result;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {

            RestoreDialog diag = (RestoreDialog) getActivity()
                    .getSupportFragmentManager().findFragmentByTag(
                            RestoreDialog.class.getName());

            if (values[0] == 0) {
                diag.bar.setMax(values[1]);
            }
            diag.bar.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Integer result) {

            Toast toast = Toast.makeText(getActivity(), result,
                    Toast.LENGTH_SHORT);
            toast.show();

            FragmentManager manager = getActivity().getSupportFragmentManager();

            DialogFragment diag = (DialogFragment) manager
                    .findFragmentByTag(RestoreDialog.class.getName());

            diag.dismiss();

            manager.beginTransaction()
                    .remove(manager
                            .findFragmentByTag(TaskManagerFragment.DEFAULT_TAG))
                    .commit();

        }

        @Override
        protected void onCancelled() {
            Toast toast = Toast.makeText(getActivity(),
                    R.string.error_backup_not_found, Toast.LENGTH_SHORT);
            toast.show();

            FragmentManager manager = getActivity().getSupportFragmentManager();

            DialogFragment diag = (DialogFragment) manager
                    .findFragmentByTag(RestoreDialog.class.getName());

            diag.dismiss();

            manager.beginTransaction()
                    .remove(manager
                            .findFragmentByTag(TaskManagerFragment.DEFAULT_TAG))
                    .commit();
        }

        private void extractEntry(final ZipEntry entry, InputStream is)
                throws IOException {

            File output;
            String name = entry.getName();

            progress[0]++;
//            publishProgress(progress);

            if (!name.contains("files")) {
                output = new File(dataFile, name);
            } else {
                if (saveOnInternal)
                    output = new File(intFilesDir, name.replaceFirst("files/",
                            ""));
                else
                    output = new File(extFilesDir, name.replaceFirst("files/",
                            ""));
            }
            output.getParentFile().mkdirs();

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(output);
                final byte[] buf = new byte[1024];
                int length;
                while ((length = is.read(buf, 0, buf.length)) >= 0) {
                    fos.write(buf, 0, length);
                }
            } catch (FileNotFoundException e) {

            } catch (IOException ioex) {
                ioex.printStackTrace();
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }
        }

    }
}
