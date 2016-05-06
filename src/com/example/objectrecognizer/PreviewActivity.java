package com.example.objectrecognizer;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class PreviewActivity extends Activity {

	/** プログレスダイアログ */
    private static ProgressDialog waitDialog;
    private static JSONObject res = new JSONObject();
	private static Bitmap bitmap;
	private static Bitmap __outBitmap;
	private static byte[] outputByteArray;
	private static String _imagePath = "";
	private static final int REQUEST_GALLERY = 0;
	private static final int MASK = 1; // 1ならマスク画像生成、0なら普通に色分け

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preview);

		//Intentを取得
		Intent intent = getIntent();
		//intentから指定キーの文字列を取得する
		Uri data = intent.getData();

		// imageSizeMaxの大きさに合わせる
		BitmapFactory.Options imageOptions = new BitmapFactory.Options();
		imageOptions.inJustDecodeBounds = true;

		int imageSizeMax = 500;
		InputStream inputStream;
		try {
			inputStream = getContentResolver().openInputStream(data);
		} catch (FileNotFoundException e1) {
			// TODO 自動生成された catch ブロック
			e1.printStackTrace();
			return;
		}
		BitmapFactory.decodeStream(inputStream, null, imageOptions);
		try {
			inputStream.close();
		} catch (IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		try {
			inputStream = getContentResolver().openInputStream(data);
		} catch (FileNotFoundException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		float imageScaleWidth = (float)imageOptions.outWidth / imageSizeMax;
		float imageScaleHeight = (float)imageOptions.outHeight / imageSizeMax;

		// もしも、縮小できるサイズならば、縮小して読み込む
		if (imageScaleWidth > 2 && imageScaleHeight > 2) {
		    BitmapFactory.Options imageOptions2 = new BitmapFactory.Options();

		    // 縦横、小さい方に縮小するスケールを合わせる
		    int imageScale = (int)Math.floor((imageScaleWidth > imageScaleHeight ?  imageScaleWidth : imageScaleHeight));

		    // inSampleSizeには2のべき上が入るべきなので、imageScaleに最も近く、かつそれ以下の2のべき上の数を探す
		    for (int i = 2; i <= imageScale; i *= 2) {
		        imageOptions.inSampleSize = i;
		    }
		    imageOptions.inSampleSize *= 2;
		    imageOptions.inJustDecodeBounds = false;
		    imageOptions.inMutable = true;
		    bitmap = BitmapFactory.decodeStream(inputStream, null, imageOptions);
		    Log.v("image", "Sample Size: 1/" + imageOptions2.inSampleSize);
		} else {
		    bitmap = BitmapFactory.decodeStream(inputStream);
		}

		try {
			inputStream.close();
		} catch (IOException e1) {
			// TODO 自動生成された catch ブロック
			e1.printStackTrace();
		}
		setImageView(false);
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		// compressメソッドの第2引数はcompressorへのヒント用に0-100の値を入れる
		bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
		byte[] bytes = stream.toByteArray();
		// byte配列をBase64(String)に変換、第2引数にBase64.NO_WRAPを指定すると一定間隔で入ってしまう改行が消せる
		String strBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
		postRequest(strBase64);

		// インスタンス作成
        waitDialog = new ProgressDialog(this);
        // タイトル設定
        waitDialog.setTitle("処理中...");
        // メッセージ設定
        waitDialog.setMessage("Please wait...");
        // スタイル設定 スピナー
        waitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        // キャンセル可能か(バックキーでキャンセル）
        waitDialog.setCancelable(true);
        // ダイアログ表示
        waitDialog.show();
	}

	/**
	 * POSTリクエストでbase64エンコードした画像データを計算サーバーへ送信
	 */
	private void postRequest(String base64) {
		final String url = "http://192.168.112.174:8080/segmentation/for_multilabel/";
		final String tag_json_obj = "json_obj_req_tag";
		// 送信したいパラメータ
		//JSONObject params = new JSONObject();

		try {
            // サーバへ送信するデータ
            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("title", base64);

            // サーバへデータ送信
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, jsonRequest, myListener, myErrorListener);
         // リクエストのタイムアウトの設定
    		jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
    				15000,
    				DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
    				DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            // リクエストキューにリクエストを追加
    		AppController.getInstance().addToRequestQueue(jsonObjectRequest, tag_json_obj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
	}

	/**
     * レスポンス受信のリスナー
     */
	private Listener<JSONObject> myListener = new Listener<JSONObject>() {
        @Override
        public void onResponse(JSONObject response) {
            Log.d("TEST", "Response success!");
            setResponseData(response);		// アノテーションデータをセット

    		try {
    			JSONObject json = new JSONObject();
    			json = getResponseData();
    			// アノテーションデータが受信できているかチェック
    			if (json.get("1") == JSONObject.NULL) {
    				//Log.e("json", "error");
    				return;
    			}
    			// アノテーションデータを基にビットマップに色付けする
    			findObject(json);
    			waitDialog.dismiss();
    			setImageView(true);
    		} catch (JSONException e) {
    			// TODO 自動生成された catch ブロック
    			e.printStackTrace();
    		}
        }
    };

    /**
     * リクエストエラーのリスナー
     */
    private ErrorListener myErrorListener = new ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.e("TEST", error.getMessage());
        }
    };

	/**
	 * 計算サーバーのレスポンスを基に20クラスのセグメンテーション
	 * @param json
	 */
	private void findObject(JSONObject json) {
		// jsonで色付け
        Bitmap outBitMap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        int width = outBitMap.getWidth();
        int height = outBitMap.getHeight();

        for (int y = 0; y < height; y++) {
        	for (int x = 0; x < width; x++) {
        		// アノテーション結果をもらう(0か15にラベリングされているはず)
            	int labelData = -1;
            	int pixelColor = outBitMap.getPixel(x, y);
        		int R = Color.red(pixelColor);
        		int G = Color.green(pixelColor);
        		int B = Color.blue(pixelColor);
				try {
					labelData = Integer.parseInt(json.getString(String.valueOf(y * width + x)));
				} catch (NumberFormatException e) {
					// TODO 自動生成された catch ブロック
					e.printStackTrace();
				} catch (JSONException e) {
					// TODO 自動生成された catch ブロック
					//e.printStackTrace();
				} finally {
					if (labelData == -1) {
						return;
					}
				}

				if (MASK == 1) {
					if (labelData == 0) {
						R = 255;
						G = 255;
						B = 255;
					} else {
						R = 0;
						G = 0;
						B = 0;
					}
				} else {
					switch (labelData) {
					case 0:
						R = (R + 0 > 255) ? 255 : R + 0;
		        		G = (G + 0 > 255) ? 255 : G + 0;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 1:
						R = (R + 128 > 255) ? 255 : R + 128;
		        		G = (G + 0 > 255) ? 255 : G + 0;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 2:
						R = (R + 0 > 255) ? 255 : R + 0;
		        		G = (G + 128 > 255) ? 255 : G + 128;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 3:
						R = (R + 128 > 255) ? 255 : R + 128;
		        		G = (G + 128 > 255) ? 255 : G + 128;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 4:
						R = (R + 0 > 255) ? 255 : R + 0;
		        		G = (G + 0 > 255) ? 255 : G + 0;
		        		B = (R + 128 > 255) ? 255 : B + 129;
		        		break;
					case 5:
						R = (R + 128 > 255) ? 255 : R + 128;
		        		G = (G + 0 > 255) ? 255 : G + 0;
		        		B = (R + 128 > 255) ? 255 : B + 128;
		        		break;
					case 6:
						R = (R + 0 > 255) ? 255 : R + 0;
		        		G = (G + 128 > 255) ? 255 : G + 128;
		        		B = (R + 128 > 255) ? 255 : B + 128;
		        		break;
					case 7:
						R = (R + 128 > 255) ? 255 : R + 128;
		        		G = (G + 128 > 255) ? 255 : G + 128;
		        		B = (R + 128 > 255) ? 255 : B + 128;
		        		break;
					case 8:
						R = (R + 64 > 255) ? 255 : R + 64;
		        		G = (G + 0 > 255) ? 255 : G + 0;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 9:
						R = (R + 192 > 255) ? 255 : R + 192;
		        		G = (G + 0 > 255) ? 255 : G + 0;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 10:
						R = (R + 64 > 255) ? 255 : R + 64;
		        		G = (G + 128 > 255) ? 255 : G + 128;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 11:
						R = (R + 192 > 255) ? 255 : R + 192;
		        		G = (G + 128 > 255) ? 255 : G + 128;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 12:
						R = (R + 64 > 255) ? 255 : R + 64;
		        		G = (G + 0 > 255) ? 255 : G + 0;
		        		B = (R + 128 > 255) ? 255 : B + 128;
		        		break;
					case 13:
						R = (R + 192 > 255) ? 255 : R + 192;
		        		G = (G + 0 > 255) ? 255 : G + 0;
		        		B = (R + 128 > 255) ? 255 : B + 128;
		        		break;
					case 14:
						R = (R + 64 > 255) ? 255 : R + 64;
		        		G = (G + 128 > 255) ? 255 : G + 128;
		        		B = (R + 128 > 255) ? 255 : B + 128;
		        		break;
					case 15:
						R = (R + 192 > 255) ? 255 : R + 192;
		        		G = (G + 128 > 255) ? 255 : G + 128;
		        		B = (R + 128 > 255) ? 255 : B + 128;
		        		break;
					case 16:
						R = (R + 0 > 255) ? 255 : R + 0;
		        		G = (G + 64 > 255) ? 255 : G + 64;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 17:
						R = (R + 128 > 255) ? 255 : R + 128;
		        		G = (G + 64 > 255) ? 255 : G + 64;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 18:
						R = (R + 0 > 255) ? 255 : R + 0;
		        		G = (G + 192 > 255) ? 255 : G + 192;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 19:
						R = (R + 128 > 255) ? 255 : R + 128;
		        		G = (G + 192 > 255) ? 255 : G +192;
		        		B = (R + 0 > 255) ? 255 : B + 0;
		        		break;
					case 20:
						R = (R + 0 > 255) ? 255 : R + 0;
		        		G = (G + 64 > 255) ? 255 : G + 64;
		        		B = (R + 128 > 255) ? 255 : B + 128;
		        		break;
					}
				}
				outBitMap.setPixel(x, y, Color.rgb(R, G, B));
        	}
        }
        __outBitmap = outBitMap.copy(Bitmap.Config.ARGB_8888, true);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		outBitMap.compress(CompressFormat.JPEG, 100, bos);
		try {
			bos.flush();
		} catch (IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		outputByteArray = bos.toByteArray();
		//bitmap.recycle();
		//outBitMap.recycle();
	}

	private JSONObject getResponseData() {
		return res;
	}

	private void setResponseData(JSONObject json) {
		res = json;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.preview, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.

		int id = item.getItemId();
		if (id == R.id.action_save) {
			// セグメンテーションした画像を保存する
			final Calendar calendar = Calendar.getInstance();
			final String hour = String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));
			final String minute = String.valueOf(calendar.get(Calendar.MINUTE));
			final String second = String.valueOf(calendar.get(Calendar.SECOND));
			_imagePath = Environment.getExternalStorageDirectory().getPath() + "/DCIM/PersonFinder/segment_" + hour + "_" + minute + "_" + second + ".jpg";
			try {
				FileOutputStream foStream = new FileOutputStream(_imagePath);
				foStream.write(outputByteArray);
				foStream.close();
				Toast.makeText(this, "画像を保存しました。", Toast.LENGTH_LONG).show();
				// ギャラリーへスキャンを促す
				MediaScannerConnection.scanFile(
						this,
						new String[]{Uri.parse(_imagePath).getPath()},
						new String[]{"image/jpeg"},
						null
				);
			} catch(Error e) {

			} catch (FileNotFoundException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
		} else if (id == R.id.action_open_album) {
			// アルバムアプリを開く
			if (_imagePath.equals("")) {
				Toast.makeText(this, "画像を保存してからタップしてください。", Toast.LENGTH_LONG).show();
				return true;
			}
			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_PICK);
			startActivityForResult(intent,REQUEST_GALLERY);
		}

		//return super.onOptionsItemSelected(item);
		return true;
	}

	private Point getDisplaySize() {
		Display display = getWindowManager().getDefaultDisplay();
		Point size = new Point();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
		    display.getSize(size);
		} else {
		    size.x = display.getWidth();
		    size.y = display.getHeight();
		}
		return size;
	}

	/**
	 * 画面上部に処理前と処理後の画像を表示する
	 * @param is_processed
	 * セグメンテーション前か後か
	 */
	private void setImageView(boolean is_processed)
	{
		ImageView iv = (ImageView)findViewById(R.id.imageView1);

	    //画面サイズを取得する
		LinearLayout.LayoutParams lp;
		float factor = 0;
		Point size = getDisplaySize();
		// 処理前を表示するのか処理後を表示するのかで分ける
		if (is_processed) {
			iv.setImageBitmap(__outBitmap);
			//画面の幅(Pixel)÷画像ファイルの幅(Pixel)＝画面いっぱいに表示する場合の倍率
			if (__outBitmap.getWidth() > 450) {
				factor =  size.x / __outBitmap.getWidth();
			} else {//450px以下なら1.2倍して大きめに
				factor =  size.x / __outBitmap.getWidth() * (float)1.2;
			}
			//表示サイズ(Pixel)を指定して、LayoutParamsを生成(ImageViewはこのサイズになる)
			lp = new LinearLayout.LayoutParams(
					(int)(__outBitmap.getWidth()*factor), (int)(__outBitmap.getHeight()*factor));
		} else {
			iv.setImageBitmap(bitmap);
			factor =  size.x / bitmap.getWidth();
			lp = new LinearLayout.LayoutParams(
					(int)(bitmap.getWidth()*factor), (int)(bitmap.getHeight()*factor));
		}

	    //中央に表示する
		lp.gravity = Gravity.CENTER;

	    //LayoutParamsをImageViewに設定
		iv.setLayoutParams(lp);

		//ImageViewのMatrixに拡大率を指定
		Matrix m = iv.getImageMatrix();
		m.reset();
		m.postScale(factor, factor);
		iv.setImageMatrix(m);
	}

	/*
	class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback {
		private SurfaceHodler mHolder;

		public MySurfaceView(Context context) {
			super(context);

			getHolder().addCallBack(this);
		}


        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        	mHolder = holder;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }
	}
	*/
}
