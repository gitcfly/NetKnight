package com.pencilbox.netknight.view;

import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.pencilbox.netknight.R;
import com.pencilbox.netknight.presentor.DairyImpl;
import com.pencilbox.netknight.presentor.IDairyPresenter;

import java.util.ArrayList;

public class NetWifi extends Fragment implements IDairyView{
    private LineChart wifilineChart;

    private IDairyPresenter mIDairyPresenter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.net_wifi, container, false);
        wifilineChart = (LineChart) view.findViewById(R.id.wifi_chart);

        mIDairyPresenter = new DairyImpl(this);
        mIDairyPresenter.showUseOfWifi(wifilineChart);

        return view;


    }

    @Override
    public void onLoadDatachartList(BaseAdapter adapter) {

    }

    @Override
    public void getshowDataofWifi(long wifiSize) {

    }

    @Override
    public void getshowDataofCelluar(long mobileSize) {

    }

    @Override
    public void getshowDataofWifiCelluar(long wificelluarSize) {

    }

    @Override
    public void getDataOfWifi(ArrayList<Entry> yValue) {

    }

    @Override
    public void getDataOfCelluar(ArrayList<Entry> yValue) {

    }

    @Override
    public void getDataOfCelluarWifi(ArrayList<Entry> yValue) {

    }

    @Override
    public void onDatachartListRefresh() {

    }
}
