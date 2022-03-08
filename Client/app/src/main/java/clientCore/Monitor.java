package clientCore;

import android.app.ProgressDialog;

public class Monitor {
    ProgressDialog progressDialog;

    public Monitor(ProgressDialog progressDialog) {
         this.progressDialog = progressDialog;
         this.progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
         this.progressDialog.setTitle("等一下！");
         this.progressDialog.setMessage("");
    }

    public void tell(DataInfo dataInfo) {
        synchronized (this) {
            progressDialog.getOwnerActivity().runOnUiThread(() -> {
                progressDialog.setTitle(dataInfo.getType().toString());
                progressDialog.setMessage(dataInfo.getFilename());

                double percentage = ((double) dataInfo.getProgressed() / dataInfo.getSize()) * 100;

                progressDialog.setProgress((int) percentage);
            });
        }
    }
}
