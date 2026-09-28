package androidx.appcompat.app;
public class AlertDialog extends android.app.Dialog {
  protected AlertDialog(android.content.Context c) { super(c); }
  public static class Builder {
    public Builder(android.content.Context c) { throw new RuntimeException("stub"); }
    public Builder setView(android.view.View v) { return this; }
    public Builder setTitle(CharSequence t) { return this; }
    public Builder setTitle(int t) { return this; }
    public Builder setMessage(CharSequence t) { return this; }
    public Builder setMessage(int t) { return this; }
    public Builder setPositiveButton(int t, android.content.DialogInterface.OnClickListener l) { return this; }
    public Builder setNegativeButton(int t, android.content.DialogInterface.OnClickListener l) { return this; }
    public Builder setNeutralButton(int t, android.content.DialogInterface.OnClickListener l) { return this; }
    public Builder setCancelable(boolean b) { return this; }
    public AlertDialog create() { return null; }
    public AlertDialog show() { return null; }
  }
}
