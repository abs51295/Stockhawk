package com.sam_chordas.android.stockhawk.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import com.sam_chordas.android.stockhawk.R;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.view.LineChartView;

/**
 * @author ABS-VIRUS
 * @version 1.0
 * @date 5/29/2016
 */
public class DetailActivity extends AppCompatActivity {
    public static final String TAG = DetailActivity.class.getSimpleName();
    private View errorMessage;
    private View progressCircle;
    private lecho.lib.hellocharts.view.LineChartView lineChart;

    private boolean isLoaded = false;
    private String companySymbol;
    private String companyName;
    private ArrayList<String> labels;
    private ArrayList<Float> values;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_line_graph);
        errorMessage = findViewById(R.id.error_message);
        progressCircle = findViewById(R.id.progress_circle);
        lineChart = (LineChartView) findViewById(R.id.stock_chart);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        companySymbol = getIntent().getStringExtra("symbol");
        if (savedInstanceState == null) {
            downloadStockDetails();
        }
    }

    // Save/Restore activity state
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (isLoaded) {
            outState.putString("company_name", companyName);
            outState.putStringArrayList("labels", labels);

            float[] valuesArray = new float[values.size()];
            for (int i = 0; i < valuesArray.length; i++) {
                valuesArray[i] = values.get(i);
            }
            outState.putFloatArray("values", valuesArray);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey("company_name")) {
            isLoaded = true;
            companyName = savedInstanceState.getString("company_name");
            labels = savedInstanceState.getStringArrayList("labels");
            values = new ArrayList<>();

            float[] valuesArray = savedInstanceState.getFloatArray("values");
            for (float f : valuesArray) {
                values.add(f);
            }
            onDownloadCompleted();
        }

    }

    // Home button click
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else {
            return false;
        }
    }

    // Download and JSON parsing
    private void downloadStockDetails() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("http://chartapi.finance.yahoo.com/instrument/1.0/" + companySymbol + "/chartdata;type=quote;range=1m/json")
                .build();
        Log.d(TAG, "Testing: " + request);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Response response) throws IOException {
                if (response.code() == 200) {
                    try {
                        // Trim response string
                        String result = response.body().string();
                        if (result.startsWith("finance_charts_json_callback( ")) {
                            result = result.substring(29, result.length() - 2);
                        }

                        // Parse JSON
                        JSONObject object = new JSONObject(result);
                        companyName = object.getJSONObject("meta").getString("Company-Name");
                        labels = new ArrayList<>();
                        values = new ArrayList<>();
                        JSONArray series = object.getJSONArray("series");
                        for (int i = 0; i < series.length(); i++) {
                            JSONObject seriesItem = series.getJSONObject(i);
                            SimpleDateFormat srcFormat = new SimpleDateFormat("yyyyMMdd");
                            String date = android.text.format.DateFormat.
                                    getMediumDateFormat(getApplicationContext()).
                                    format(srcFormat.parse(seriesItem.getString("Date")));
                            labels.add(date);
                            values.add(Float.parseFloat(seriesItem.getString("close")));
                        }

                        onDownloadCompleted();
                    } catch (Exception e) {
                        onDownloadFailed();
                        e.printStackTrace();
                    }
                } else {
                    onDownloadFailed();
                }
            }

            @Override
            public void onFailure(Request request, IOException e) {
                onDownloadFailed();
            }
        });
    }

    private void onDownloadCompleted() {
        DetailActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setTitle(companyName);

                List<AxisValue> axisValuesX = new ArrayList<>();
                List<PointValue> pointValues = new ArrayList<>();
                int counter = -1;

                for (int i = 0; i < labels.size(); i++) {
                    counter++;
                    int x = labels.size() - 1 - counter;
                    if(i%3==0) {
                        PointValue pointValue = new PointValue(x, values.get(i));
                        pointValue.setLabel(labels.get(i));
                        pointValues.add(pointValue);
                        AxisValue axisValueX = new AxisValue(x);
                        axisValueX.setLabel(labels.get(i));
                        axisValuesX.add(axisValueX);
                    }
                }

                Line line = new Line(pointValues).setColor(Color.WHITE).setCubic(false);
                List<Line> lines = new ArrayList<>();
                lines.add(line);
                LineChartData lineChartData = new LineChartData();
                lineChartData.setLines(lines);

                // Init x-axis
                Axis axisX = new Axis(axisValuesX);
                axisX.setHasLines(true);
                axisX.setMaxLabelChars(4);
                lineChartData.setAxisXBottom(axisX);

                // Init y-axis
                Axis axisY = new Axis();
                axisY.setAutoGenerated(true);
                axisY.setHasLines(true);
                axisY.setMaxLabelChars(4);
                lineChartData.setAxisYLeft(axisY);

                // Update chart with new data.
                lineChart.setInteractive(false);
                lineChart.setLineChartData(lineChartData);

                // Show chart
                lineChart.setVisibility(View.VISIBLE);


                progressCircle.setVisibility(View.GONE);
                errorMessage.setVisibility(View.GONE);

                isLoaded = true;
            }
        });
    }

    private void onDownloadFailed() {
        DetailActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lineChart.setVisibility(View.GONE);
                progressCircle.setVisibility(View.GONE);
                errorMessage.setVisibility(View.VISIBLE);
                setTitle(R.string.error);
            }
        });
    }
}