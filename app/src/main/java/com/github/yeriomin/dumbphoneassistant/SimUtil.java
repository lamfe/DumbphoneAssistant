package com.github.yeriomin.dumbphoneassistant;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;

import java.util.ArrayList;

public class SimUtil extends Util {

    private final SharedPreferences settings;
    private final TelephonyManager telephony;
    private Uri simUri;

    private int maxContactNameLength = 0; // Maximum length of contact names may
                                          // differ from SIM to SIM, will be
                                          // detected upon load of class

    public SimUtil(Activity activity) {
        super(activity);

        this.settings = activity.getSharedPreferences(activity.getApplicationInfo().name, 0);
        this.telephony = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
        this.simUri = Uri.parse(detectSimUri());

        maxContactNameLength = getMaxContactNameLength();
    }

    /**
     * Detects the URI identifier for accessing the SIM card. Is different,
     * depending on Android version.
     * 
     * @return Uri of the SIM card on this system
     */
    private String detectSimUri() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.DONUT
                ? "content://icc/adn"
                : "content://sim/adn"
                ;
    }

    /**
     * Query SharedPreferences storage for the maximum contact length
     * If no value is stored, detect it by attempting to write a contact, then store it
     * The value is stored with current SIM card's id
     *
     * @return Length of the longest contact name the SIM card accepts
     */
    private int getMaxContactNameLength() {
        String simId = this.telephony.getSimSerialNumber();
        int maxContactLength = this.settings.getInt(simId, 0);
        if (maxContactLength == 0) {
            maxContactLength = detectMaxContactNameLength();
        }
        if (maxContactLength > 0) {
            this.settings.edit().putInt(simId, maxContactLength);
        }
        return maxContactLength;
    }

    /**
     * Detects the maximum length of a contacts name which is accepted by the
     * SIM card by attempting to insert contacts until the SIM card accepts
     * 
     * @return Length of the longest contact name the SIM card accepts
     */
    private int detectMaxContactNameLength() {
        String nameString = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"; // 52 chars
        int currentMax;
        boolean success = false;
        Contact testContact = null;

        // loop from longest to shortest contact name length until a contact is stored successfully
        for (currentMax = nameString.length(); (!success && currentMax > 0); currentMax--) {
            testContact = new Contact(null, nameString.substring(0, currentMax), "24448888888");
            success = create(testContact);
        }

        // if stored successfully, remove contact
        delete(testContact);

        return currentMax;
    }

    /**
     * Retrieves all contacts from the SIM card.
     * 
     * @return ArrayList containing Contact objects from the stored SIM information
     */
    public ArrayList<Contact> get() {
        final String[] simProjection = new String[] {
                android.provider.Contacts.PeopleColumns.NAME,
                android.provider.Contacts.PhonesColumns.NUMBER,
                android.provider.BaseColumns._ID
        };
        Cursor results = resolver.query(
                simUri,
                simProjection,
                null,
                null,
                android.provider.Contacts.PeopleColumns.NAME
        );

        final ArrayList<Contact> simContacts = new ArrayList<Contact>();
        if (results != null) {
            if (results.getCount() > 0) {
                while (results.moveToNext()) {
                    final Contact simContact = new Contact(
                            results.getString(results.getColumnIndex(android.provider.BaseColumns._ID)),
                            results.getString(results.getColumnIndex(android.provider.Contacts.PeopleColumns.NAME)),
                            results.getString(results.getColumnIndex(android.provider.Contacts.PhonesColumns.NUMBER))
                    );
                    simContacts.add(simContact);
                }
            }
            results.close();
        }
        return simContacts;
    }

    /**
     * Creates a contact on the SIM card.
     * 
     * @param newSimContact
     *            The Contact object containing the name and number of the
     *            contact
     * @return Success or failure. ContentResolver doesn't dive any other info
     */
    public boolean create(Contact newSimContact) {
        ContentValues newSimValues = new ContentValues();
        newSimValues.put("tag", newSimContact.getName());
        newSimValues.put("number", newSimContact.getNumber());
        Uri newSimRow = resolver.insert(simUri, newSimValues);

        // It is always "content://icc/adn/0" on success and null on failure
        // TODO: Isn't there a better API for working with SIM?
        return newSimRow != null;
    }

    /**
     * Delete a contact on the SIM card. Will only be removed if identified
     * uniquely. Identification happens on the contact.name and contact.number
     * attributes.
     * 
     * @param contact The contact to delete.
     * @return Success or not
     */
    public boolean delete(Contact contact) {
        String where = "tag='?' AND number='?'";
        String[] selectionArgs = new String[] {contact.getName(), contact.getNumber()};
        return resolver.delete(simUri, where, selectionArgs) > 0;
    }

    /**
     * Converts a contact to a SIM card conforming contact by stripping the name
     * to the maximum allowed length and setting ID to null.
     * 
     * @param contact
     *            The contact to convert to SIM conforming values
     * @return a contact which does not contain values which exceed the SIM
     *         cards limits or null if there was a problem detecting the limits
     */
    public Contact convertToSimContact(Contact contact) {
        String name = maxContactNameLength > 0
                ? contact.getName().substring(0, Math.min(contact.getName().length(), maxContactNameLength))
                : contact.getName()
                ;
        String number = contact.getNumber().replace("-", "");
        return new Contact(null, name, number);
    }
}