package com.mobilecomputing.pecunia.controller;

import com.mobilecomputing.pecunia.application.BillingCalculator;
import com.mobilecomputing.pecunia.model.*;
import com.mobilecomputing.pecunia.repository.NotificationRepository;
import com.mobilecomputing.pecunia.repository.TransactionRepository;
import com.mobilecomputing.pecunia.repository.TripRepository;
import com.mobilecomputing.pecunia.repository.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping(value = "/trip")
public class TripController {

    @Autowired
    TripRepository tripRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    BillingCalculator billingCalculator;

    /**
     * This function returns a trip by the trip id
     * @param TripId
     * @return
     */
    @GetMapping("/getTripById")
    public ResponseEntity getTripById(@RequestParam String TripId) {
        try{
            return ResponseEntity.ok(tripRepository.findById(TripId).get());
        }catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip not found");
        }
    }

    @GetMapping("/getAllTrips")
    public ResponseEntity getAllTrips() {
        ArrayList<Trip> response = new ArrayList<>();
        tripRepository.findAll().forEach(trip -> {
            response.add(trip);
        });

        if(response.size()==0){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * This functions adds a new trip to the database
     * @param trip
     * @param creator
     * @return
     */
    @PostMapping("/addTrip")
    public ResponseEntity addTrip(@RequestBody Trip trip,@RequestParam String creator) {
        ArrayList<String> participants = new ArrayList<>();
        ArrayList<String> userList = new ArrayList<>();
        userRepository.findAll().forEach(user -> {
            userList.add(user.geteMail());
        });
        try{
            trip.getTripParticipants().forEach(participant->{
                if(userList.contains(participant)){
                    participants.add(participant);
                    Notification tempNotification = new Notification();
                    tempNotification.setTripName(trip.getTripName());
                    tempNotification.setTripId(trip.getTripId());
                    tempNotification.setUserId(participant);
                    tempNotification.setNotificationType(2);
                    tempNotification.setNotificationMessage("You were added to the trip "+trip.getTripName()); //TODO Nachricht bestimmen
                    if(!participant.equals(creator)){
                        notificationRepository.save(tempNotification);
                    }
                }
                trip.setTripParticipants(participants);
            });
        }catch(NoSuchElementException e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(tripRepository.save(trip).getTripId());
    }

    /**
     * This function adds an admin to the trip
     * @param eMail E-Mail of the admin
     * @param TripId
     * @return
     */
    @PostMapping("/addAdminToTrip")
    public ResponseEntity addAdminToTrip(@RequestParam String eMail,@RequestParam String TripId){
        try{
            Trip trip =tripRepository.findById(TripId).get();
            trip.getAdmins().add(eMail);
            tripRepository.save(trip);
            Notification notification = new Notification();
            notification.setNotificationType(4);
            notification.setUserId(eMail);
            notification.setTripId(TripId);
            notification.setTripName(trip.getTripName());
            notification.setNotificationMessage("You are now admin of the trip "+trip.getTripName());
            notificationRepository.save(notification);
            return ResponseEntity.ok(HttpStatus.OK);
        }catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip not found");
        }
    }

    /**
     * This function delete an specific admin of the trip
     * @param eMail
     * @param TripId
     * @return
     */
    @DeleteMapping("/deleteAdmin")
    public ResponseEntity deleteAdmin(@RequestParam String eMail, @RequestParam String TripId){
        try{
            Trip trip= tripRepository.findById(TripId).get();
            trip.getAdmins().remove(eMail);
            tripRepository.save(trip);
            return ResponseEntity.ok(HttpStatus.OK);
        }catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip not found");
        }
    }

    /**
     * This function returns all trips in which the specific user is signed
     * @param eMail
     * @return
     */
    @GetMapping("/getTripsByUser")
    public ResponseEntity getTripsByUser(@RequestParam String eMail) {

        ArrayList<Trip> temp = new ArrayList<>();
        ArrayList<Trip> response = new ArrayList<>();
        try{
            tripRepository.findAll().forEach(trip -> {
                temp.add(trip);
            });

            for (Trip t: temp) {
                boolean userIsParticipant = false;
                for(String tempEmail: t.getTripParticipants()){
                    if(tempEmail.equals(eMail)){
                        userIsParticipant=true;
                    }
                }

                if(userIsParticipant){
                    response.add(t);
                }
            }

            if(response.size()==0){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User has no trip");
            }

            return ResponseEntity.ok(response);

        }catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
    }

    @GetMapping("/getBillFromTrip")
    public ResponseEntity getBillFromTrip(@RequestParam String tripId){
        try{
            ArrayList<String> responseList = new ArrayList<>();
           String billingString= billingCalculator.calcBill(tripRepository.findById(tripId).get());
           responseList.add(billingString);
            tripRepository.deleteById(tripId);
            return ResponseEntity.status(HttpStatus.OK).body(responseList);
        }catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip not found");
        }
    }

    @DeleteMapping("/deleteTrip")
    public ResponseEntity deleteTrip(@RequestParam String tripId){
        try{
            tripRepository.findById(tripId).get();
            tripRepository.deleteById(tripId);
            return ResponseEntity.ok(HttpStatus.OK);
        }catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip not found");
        }
    }

    @GetMapping("/getCompleteTripById")
    public ResponseEntity getCompleteTripById(@RequestParam String tripId){
        try{
            CompleteTrip trip = new CompleteTrip();
            Trip tempTrip=tripRepository.findById(tripId).get();
            ArrayList<Transaction> tempTransactionList =new ArrayList<>();
            ArrayList<User> tempParticipantList = new ArrayList<>();

            trip.setTripName(tempTrip.getTripName());
            trip.setTripDuration(tempTrip.getTripDuration());
            trip.setAdmins(tempTrip.getAdmins());
            trip.setCurrency(tempTrip.getCurrency());
            trip.setImageBase64String(tempTrip.getImageBase64String());
            trip.setTripDuration(tempTrip.getTripDuration());
            trip.setTripId(tempTrip.getTripId());

            tempTrip.getTransactions().forEach(transaction->{
                tempTransactionList.add(transactionRepository.findById(transaction).get());
            });
            trip.setTransactions(tempTransactionList);

            tempTrip.getTripParticipants().forEach(participant->{
                tempParticipantList.add(userRepository.findById(participant).get());
            });
            trip.setTripParticipants(tempParticipantList);


            return ResponseEntity.ok(trip);
        }catch (NoSuchElementException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip not found");
        }
    }

    @DeleteMapping("/deleteUserFromTrip")
    public ResponseEntity deleteUserFromTrip(@RequestParam String eMail,@RequestParam String tripId,@RequestBody Notification notification){
        try{
            Trip trip =tripRepository.findById(tripId).get();
            trip.getTransactions().forEach(transaction->{
                if(transaction.equals(eMail)){
                    transactionRepository.deleteById(transaction);
                }
            });
            for(int i=0;i<trip.getAdmins().size();i++){
                if(trip.getAdmins().get(i).equals(eMail)){
                    trip.getAdmins().remove(i);
                }
            }
            trip.getTripParticipants().remove(eMail);
            tripRepository.save(trip);
            if(notification.getUserId()!=null){
                notificationRepository.save(notification);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(HttpStatus.OK);
    }
}
