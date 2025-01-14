package com.pencilbox.netknight.view;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.pencilbox.netknight.R;
import com.pencilbox.netknight.presentor.IAppInfoUseImpl;
import com.pencilbox.netknight.presentor.IAppInfoUsePresenter;

/*
 * Created by wu on 16/7/11.
 */

public class NetAppUse extends Fragment implements IAppUseInfoView {
    private ListView appuse_listView = null;
    private IAppInfoUsePresenter mIAppInfoUsePresenter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.net_appuse, container, false);
        appuse_listView = (ListView) view.findViewById(R.id.appuse_listview);
        mIAppInfoUsePresenter = new IAppInfoUseImpl(getActivity(), this);
        mIAppInfoUsePresenter.loadAppList();
        return view;

    }


    @Override
    public void onLoadAppInfoUseList(BaseAdapter adapter) {
        appuse_listView.setAdapter(adapter);

        adapter.notifyDataSetChanged();


    }

    @Override
    public void onLoadAppInfoUseWifi(long wifiSize) {

    }

    @Override
    public void onLoadAppInfoUseCelluar(long mobileSize) {

    }

    @Override
    public void onListRefresh() {

    }

    @Override
    public void onOptionFailed(int optionId, String msg) {

    }
}
