package com.example.fimae.repository;

import androidx.annotation.NonNull;

import com.example.fimae.models.Conversation;
import com.example.fimae.models.Message;
import com.example.fimae.models.Participant;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

public class ChatRepository{
    FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private static ChatRepository instance;
    CollectionReference conversationsRef = firestore.collection("conversations");
    private ChatRepository() {
    }
    public static synchronized ChatRepository getInstance() {
        if (instance == null) {
            instance = new ChatRepository();
        }
        return instance;
    }
    public ListenerRegistration getConversationsRef(@NotNull EventListener<QuerySnapshot> listener){

        return conversationsRef.addSnapshotListener(listener);
    }
    public Query getConversationQuery(){
        return conversationsRef.whereArrayContains("participantIds", Objects.requireNonNull(FirebaseAuth.getInstance().getUid()));
    }
    public Task<Conversation> getOrCreateConversation(ArrayList<String> participantIds, String type) {

        TaskCompletionSource<Conversation> taskCompletionSource = new TaskCompletionSource<>();
        Collections.sort(participantIds);
        Task<QuerySnapshot> queryTask = conversationsRef
                .whereEqualTo("participantIds", participantIds)
                .whereEqualTo("type", type)
                .limit(1)
                .get();
        queryTask.addOnCompleteListener(querySnapshotTask -> {
            if (querySnapshotTask.isSuccessful()) {
                QuerySnapshot querySnapshot = querySnapshotTask.getResult();
                System.out.println("Res: " + querySnapshot.getDocuments());
                if (!querySnapshot.isEmpty()) {
                    DocumentSnapshot documentSnapshot = querySnapshot.getDocuments().get(0);
                    Conversation conversation = documentSnapshot.toObject(Conversation.class);
                    assert conversation != null;
                    taskCompletionSource.setResult(conversation);
                } else {
                    DocumentReference newConDoc = conversationsRef.document();
                    CollectionReference collectionReference = newConDoc.collection("participants");
                    Conversation conversation = new Conversation();
                    conversation.setId(newConDoc.getId());
                    conversation.setType(Conversation.FRIEND_CHAT);
                    conversation.setParticipantIds((ArrayList<String>) participantIds);
                    WriteBatch batch = FirebaseFirestore.getInstance().batch();
                    batch.set(newConDoc, conversation);
                    for (String id : participantIds) {
                        Participant participant = new Participant();
                        participant.setUid(id);
                        participant.setRole(Participant.ROLE_Participant);
                        batch.set(collectionReference.document(), participant);
                    }
                    batch.commit().addOnCompleteListener(batchTask -> {
                        if (batchTask.isSuccessful()) {
                            conversation.setId(newConDoc.getId());
                            taskCompletionSource.setResult(conversation);
                        } else {
                            taskCompletionSource.setException(Objects.requireNonNull(batchTask.getException()));
                        }
                    });
                }
            } else {
                taskCompletionSource.setException(Objects.requireNonNull(querySnapshotTask.getException()));
            }
        });
        return taskCompletionSource.getTask();
    }
    public Task<Conversation> sendTextMessage(String conversationId, String content){
        TaskCompletionSource taskCompletionSource = new TaskCompletionSource();
        if(content.isEmpty()){
            taskCompletionSource.setException(new Exception("Text message has null content"));
            return taskCompletionSource.getTask();
        }
        WriteBatch batch = firestore.batch();
        DocumentReference currentConversationRef = conversationsRef.document(conversationId);
        CollectionReference reference = currentConversationRef.collection("messages");
        DocumentReference messDoc = reference.document();
        Message message = Message.text(messDoc.getId(), content);
        batch.set(messDoc, message);
        batch.update(currentConversationRef, "lastMessage", messDoc);
        batch.commit().addOnCompleteListener(task -> {
            if(task.isSuccessful()){
                taskCompletionSource.setResult(message);
            } else {
                taskCompletionSource.setException(Objects.requireNonNull(task.getException()));
            }
        });
        return taskCompletionSource.getTask();
    }
}
