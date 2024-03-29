package com.orderfoodserver.teknomerkez.orderfoodserver;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.jaredrummler.materialspinner.MaterialSpinner;
import com.orderfoodserver.teknomerkez.orderfoodserver.Common.Common;
import com.orderfoodserver.teknomerkez.orderfoodserver.Model.DataMessage;
import com.orderfoodserver.teknomerkez.orderfoodserver.Model.MyResponse;
import com.orderfoodserver.teknomerkez.orderfoodserver.Model.Request;
import com.orderfoodserver.teknomerkez.orderfoodserver.Model.Token;
import com.orderfoodserver.teknomerkez.orderfoodserver.Remote.APIService;
import com.orderfoodserver.teknomerkez.orderfoodserver.ViewHolder.OrderViewHolder;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.orderfoodserver.teknomerkez.orderfoodserver.Common.Common.ON_MY_WAY;
import static com.orderfoodserver.teknomerkez.orderfoodserver.Common.Common.PLACED;
import static com.orderfoodserver.teknomerkez.orderfoodserver.Common.Common.SHİPPED;
import static com.orderfoodserver.teknomerkez.orderfoodserver.Common.Common.convertCodeToStatus;

public class OrderStatus extends AppCompatActivity {

    RecyclerView recyclerView;
    RecyclerView.LayoutManager layoutManager;

    FirebaseDatabase database;
    DatabaseReference request;

    FirebaseRecyclerAdapter<Request, OrderViewHolder> adapter;
    MaterialSpinner spinner;

    APIService mService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_status);

        //Firebase
        database = FirebaseDatabase.getInstance();
        request = database.getReference("Request");
        mService = Common.getFCMClient();

        // init

        recyclerView = findViewById(R.id.listOrders);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        loadOrders(); // load all orders
    }

    private void loadOrders() {
        Query query = FirebaseDatabase.getInstance()
                .getReference("Request");


        FirebaseRecyclerOptions<Request> options = new FirebaseRecyclerOptions.Builder<Request>().setQuery(query, Request.class).build();
        adapter = new FirebaseRecyclerAdapter<Request, OrderViewHolder>(options) {
            @NonNull
            @Override
            public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.order_item_layout, parent, false);
                return new OrderViewHolder(view);
            }

            @Override
            protected void onBindViewHolder(@NonNull OrderViewHolder viewHolder, final int position, @NonNull final Request model) {
                viewHolder.txtOrderID.setText(adapter.getRef(position).getKey());
                viewHolder.txtOrderStatus.setText(convertCodeToStatus(model.getStatus()));
                viewHolder.txtOrderAddress.setText(model.getAddress());
                viewHolder.txtOrderPhone.setText(model.getPhone());

                viewHolder.btnEdit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showUpdateDialaog(adapter.getRef(position).getKey(), adapter.getItem(position));
                    }
                });
                viewHolder.btnRemove.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        request.child(adapter.getRef(position).getKey()).removeValue();
                        adapter.notifyDataSetChanged();
                    }
                });
                viewHolder.btnDetail.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent orderDetail = new Intent(OrderStatus.this, OrderDetail.class);
                        Common.currentRequest = model;
                        orderDetail.putExtra(Common.GET_ORDER_ID, adapter.getRef(position).getKey());
                        startActivity(orderDetail);
                    }
                });

                viewHolder.btnDirection.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent trackingOrder = new Intent(OrderStatus.this, TrackingOrder.class);
                        Common.currentRequest = model;
                        startActivity(trackingOrder);
                    }
                });
            }

        };
        adapter.notifyDataSetChanged();
        recyclerView.setAdapter(adapter);
    }

    private void showUpdateDialaog(String key, final Request item) {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View view = inflater.inflate(R.layout.update_order_layout, null);
        spinner = view.findViewById(R.id.spinner);
        spinner.setItems(PLACED, SHİPPED, ON_MY_WAY);
        alertDialog.setTitle(Common.UPDATE_ORDER)
                .setMessage(Common.CHOOSE_STATUS)
                .setView(view);
        final String localkey = key;
        alertDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                item.setStatus(String.valueOf(spinner.getSelectedIndex()));
                request.child(localkey).setValue(item);
                adapter.notifyDataSetChanged(); // add to uptade item size
                sendOrderStatusToUser(localkey, item);
            }
        })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).show();
    }

    private void sendOrderStatusToUser(final String key, final Request item) {
        DatabaseReference token = database.getReference("Tokens");
        token.orderByKey().equalTo(item.getName()) // problem olabilir tekrar buraya bak!
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                            Token token = postSnapshot.getValue(Token.class);
                            // make raw payLoad
                            Map<String,String> dataSend = new HashMap<>();
                            dataSend.put("title", "Order Food Server");
                            dataSend.put("message", "Your order " + key + " was updated!");
                            DataMessage dataMessage = new DataMessage(token.getToken(),dataSend);

                            mService.sendNotification(dataMessage).enqueue(new Callback<MyResponse>() {
                                @Override
                                public void onResponse(Call<MyResponse> call, Response<MyResponse> response) {
                                        if (response.body().succes == 1) {
                                            Toast.makeText(OrderStatus.this, "Order was updated!", Toast.LENGTH_SHORT).show();
                                            finish();
                                        } else {
                                            Toast.makeText(OrderStatus.this, "Order was updated but failed to send notification",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                }

                                @Override
                                public void onFailure(Call<MyResponse> call, Throwable t) {
                                    Log.e("ERROR", t.getMessage());
                                }
                            });

                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
    }

   @Override
    protected void onStart() {
        super.onStart();
        adapter.startListening();
    }
    @Override
    protected void onStop() {
        super.onStop();
        adapter.stopListening();
    }
    @Override
    protected void onResume(){
        super.onResume();
        if(adapter != null)
            adapter.startListening();
    }
}
