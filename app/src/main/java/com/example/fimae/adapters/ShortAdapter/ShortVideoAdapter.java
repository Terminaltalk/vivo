package com.example.fimae.adapters.ShortAdapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.fimae.R;
import com.example.fimae.adapters.FirestoreAdapter;
import com.example.fimae.databinding.LayoutReelBinding;
import com.example.fimae.models.Fimaers;
import com.example.fimae.models.shorts.ShortMedia;
import com.example.fimae.repository.FimaerRepository;
import com.example.fimae.repository.ShortsRepository;
import com.example.fimae.utils.DoubleClickListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class ShortVideoAdapter extends FirestoreAdapter<ShortVideoAdapter.VideoHolder> {
    Context context;
    ArrayList<ShortMedia> shortMedias = new ArrayList<>();
    private ShortVideoAdapter.IClickCardListener iClickCardListener;
    boolean isPlaying = true;
    ArrayList<VideoHolder> holders = new ArrayList<>();
    String uidCurrentUser = FirebaseAuth.getInstance().getUid();

    String idVideoFirst;

    public interface IClickCardListener {
        void onClickUser(ShortMedia video);
    }

    public ShortVideoAdapter(Query query, Context context, ArrayList<ShortMedia> shortMedias, String idVideoFirst, IClickCardListener iClickCardListener) {
        super(query);
        this.context = context;
        this.shortMedias = shortMedias;
        this.idVideoFirst = idVideoFirst;
        this.iClickCardListener = iClickCardListener;
    }

    @NonNull
    @Override
    public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_reel, parent, false);
        return new ShortVideoAdapter.VideoHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull VideoHolder holder, int position) {
        ShortMedia media = shortMedias.get(position);
        //uidCurrentUser = FirebaseAuth.getInstance().getUid();
        holders.add(position, holder);
        onBeginPlayVideo(position);
        // get user avatar
        FimaerRepository.getInstance().getFimaerById(media.getUid()).addOnCompleteListener(
                task -> {
                    if(task.isSuccessful()){
                        Fimaers fimaers = task.getResult();
                        if(fimaers != null){
                            // set name and avatar
                            holder.binding.itemVideoTvName.setText(fimaers.getName());
                            Picasso.get().load(fimaers.getAvatarUrl()).placeholder(R.drawable.ic_default_avatar).into(holder.binding.shortAvatar);

                            // check follow

                            // check like
                            if(ShortsRepository.getInstance().checkUserLiked(uidCurrentUser, media)){
                                holder.binding.itemVideoIcLike.setColorFilter(ContextCompat.getColor(context, R.color.red));
                            } else {
                                holder.binding.itemVideoIcLike.setColorFilter(ContextCompat.getColor(context, R.color.white));
                            }
                        }
                    }
                }
        );
        // set video
        holder.binding.videoView.setVideoURI(Uri.parse((String) media.getMediaUrl()));
        holder.binding.itemVideoTvDescription.setText(media.getDescription());
        // set text like
        if(media.getUsersLiked() != null) {
            int likeCount = ShortsRepository.getInstance().getLikeCount(media);
            holder.binding.itemVideoTvLike.setText(getStringNumber(likeCount));
        } else {
            holder.binding.itemVideoTvLike.setText("0");
        }
        // set text comment
        String numOfComments = getStringNumber(media.getNumOfComments());
        holder.binding.itemVideoTvComment.setText(numOfComments);
        // click icon back
        holder.binding.itemVideoIcBack.setOnClickListener(view -> {
            iClickCardListener.onClickUser(media);
        });
        // video playing
        holder.binding.videoView.setOnPreparedListener(mp -> {
            mp.start();
            mp.setLooping(true);
            isPlaying = true;
            checkPlaying(holder);
            wait(300);
            holder.binding.imageThumb.setVisibility(View.GONE);
            holder.binding.loading.setVisibility(View.GONE);
        });
        // click in video
        holder.binding.videoView.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onDoubleClick() {
                handleLikeShort(media, holder);
            }

            @Override
            public void onSingleClick() {
                togglePlaying(holder);
            }
        });
        holder.binding.itemVideoIcPlay.setOnClickListener(view -> {
            togglePlaying(holder);
        });

        // follow

        // like
        holder.binding.itemVideoIcLike.setOnClickListener(view -> {
            handleLikeShort(media, holder);
        });
        // comment

        // share
    }

    private void handleLikeShort(ShortMedia media, VideoHolder holder) {
        if(ShortsRepository.getInstance().checkUserLiked(uidCurrentUser, media)){
            holder.binding.itemVideoIcLike.setColorFilter(ContextCompat.getColor(context, R.color.white));
            //holder.binding.itemVideoTvLike.setText(getStringNumber(media.getUsersLiked().size() - 1));
            holder.binding.itemVideoTvLike.setText(getStringNumber(ShortsRepository.getInstance().getLikeCount(media) - 1));
        } else {
            holder.binding.itemVideoIcLike.setColorFilter(ContextCompat.getColor(context, R.color.red));
            holder.binding.itemVideoTvLike.setText(getStringNumber(ShortsRepository.getInstance().getLikeCount(media) + 1));
        }
        ShortsRepository.getInstance().handleLikeShort(uidCurrentUser, media);
    }
    @SuppressLint("DefaultLocale")
    private String getStringNumber(int num) {
        if (num < 1000) {
            return String.valueOf(num);
        } else if (num < 1000000) {
            return String.format("%.1fK", num / 1000.0);
        } else {
            return String.format("%.1fM", num / 1000000.0);
        }
    }

    public void onBeginPlayVideo(int position) {
        ShortMedia media = shortMedias.get(position);
        VideoHolder holderPlaying = holders.get(position);
        // anh thumbnail
        long interval = 0;
        RequestOptions options = new RequestOptions().frame(interval);
        Glide.with(holderPlaying.itemView)
                .load(Uri.parse((String) media.getMediaUrl()))
                .apply(options)
                .into(holderPlaying.binding.imageThumb);
        holderPlaying.binding.imageThumb.setVisibility(View.VISIBLE);
        holderPlaying.binding.loading.setVisibility(View.VISIBLE);
        holderPlaying.binding.itemVideoIcPlay.setVisibility(View.GONE);
    }
    public static void wait(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }
    void togglePlaying(@NonNull VideoHolder holder) {
        if (isPlaying) {
            holder.binding.videoView.pause();
            isPlaying = false;
            holder.binding.itemVideoIcPlay.setVisibility(View.VISIBLE);
        } else {
            holder.binding.videoView.start();
            isPlaying = true;
            holder.binding.itemVideoIcPlay.setVisibility(View.GONE);
        }
    }

    void checkPlaying(@NonNull VideoHolder holder) {
        if (!isPlaying) {
            holder.binding.itemVideoIcPlay.setVisibility(View.VISIBLE);
        } else {
            holder.binding.itemVideoIcPlay.setVisibility(View.GONE);
        }
    }

    public void addWatched(int position) {
        ShortMedia media = shortMedias.get(position);
        ShortsRepository.getInstance().handleWatchedShort(uidCurrentUser, media);
    }

    @Override
    public void OnSuccessQueryListener(ArrayList<DocumentSnapshot> queryDocumentSnapshots) {

    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void OnSuccessQueryListener(ArrayList<DocumentSnapshot> queryDocumentSnapshots, ArrayList<DocumentChange> documentChanges) {
        if(shortMedias == null){
            shortMedias = new ArrayList<>();
        } else {
            shortMedias.clear();
        }
        for(DocumentSnapshot documentSnapshot : queryDocumentSnapshots){
            ShortMedia shortMedia = documentSnapshot.toObject(ShortMedia.class);
            assert shortMedia != null;
            if(shortMedia.getId().equals(idVideoFirst)) shortMedias.add(0, shortMedia);
            else shortMedias.add(shortMedia);
        }
        notifyDataSetChanged();
        stopListening();
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return shortMedias.size();
    }

    public class VideoHolder extends RecyclerView.ViewHolder {
        LayoutReelBinding binding;
        public VideoHolder(@NonNull View itemView) {
            super(itemView);
            binding = LayoutReelBinding.bind(itemView);
        }
    }
}
