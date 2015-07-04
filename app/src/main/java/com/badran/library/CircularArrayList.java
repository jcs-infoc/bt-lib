package com.badran.library;

import android.util.Log;
import android.widget.Switch;

import com.badran.bluetoothcontroller.PluginToUnity;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * If you use this code, please consider notifying isak at du-preez dot com
 * with a brief description of your application.
 * <p/>
 * This is free and unencumbered software released into the public domain.
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 */

public class CircularArrayList {

    private final int n; // buffer length
    private final byte[] buf; // a List implementing RandomAccess
    private final ByteBuffer buffer;
    private int head = 0;
    private int tail = 0;

    private int lengthPacketsCounter = 0;
    private int counter = 0;
    private int packetSize = 0;


    private enum MODES {
        LENGTH_PACKET,END_BYTE_PACKET,NO_PACKETIZATION



    } private MODES mode = MODES.NO_PACKETIZATION;


    private Queue<Integer> marks = new LinkedList<Integer>();

    private List<Byte> endBytes = new LinkedList<Byte>();


    public CircularArrayList(int capacity) {


        n = capacity ;
        buf = new byte[capacity];
        buffer = ByteBuffer.wrap(buf);
        marks = new LinkedList<Integer>();
    }



    public int capacity() {
        return n - 1;
    }

    private int wrapIndex(int i) {
        int m = i % n;
        if (m < 0) { // java modulus can be negative
            m += n;
        }
        return m;
    }


    public int size() {
        return tail - head + (tail < head ? n : 0);
    }


    public void addEndByte(byte byt) {
        endBytes.add(byt);
        mode = MODES.END_BYTE_PACKET;
    }

    public void setPacketSize(int size) {
        packetSize = size;
        mode = MODES.LENGTH_PACKET;
    }

    public void erasePackets(int size) {
        marks.clear();
        endBytes.clear();
        packetSize = 0;
        lengthPacketsCounter = 0;
        counter = 0;
        mode = MODES.NO_PACKETIZATION;
    }

    public synchronized int getDataSize(){
        switch (mode){
            case NO_PACKETIZATION: return 0;
            case LENGTH_PACKET : return lengthPacketsCounter;
            case END_BYTE_PACKET : return marks.size();
                default: return size();
        }

    }
    public synchronized boolean add(byte e) {//returns true if packet/data available for the first time after was no packets

        int s = size();
        if (s == n - 1) {
            throw new IllegalStateException("Cannot add element."
                    + " CircularArrayList is filled to capacity.");

        }
        boolean isFirstTimeData = false;
        switch (mode){
            case LENGTH_PACKET :
                if (counter < packetSize) {
                    counter++;
                } else {
                    counter = 0;
                    if(lengthPacketsCounter == 0)
                        isFirstTimeData = true;

                    lengthPacketsCounter++;


                }break;

            case END_BYTE_PACKET :
                if (!endBytes.isEmpty())
                    for (byte byt : endBytes) {
                        if (byt == e) {
                            if(marks.isEmpty()) isFirstTimeData = true;
                            marks.add(tail);

                        }
                    }
                break;
            case NO_PACKETIZATION: if(s == 0) isFirstTimeData = true;
        }

        tail = wrapIndex(tail + 1);
        buf[tail] = e;


        return isFirstTimeData;
    }


    public Byte poll() {

        if (size() <= 0) return null;


        byte e = buf[head];
        head = wrapIndex(head + 1);

        return e;
    }

    public synchronized byte[] pollArray (int endIndex,int id) {
        if(mode != MODES.NO_PACKETIZATION) return pollPacket(id);
        int s = size();
        if (s == 0) return null;

        boolean readAllData = false;

        if (endIndex >= s ) {
            endIndex = s - 1;
            readAllData = true;
        }

        if (endIndex < 0  ) {

            throw new IndexOutOfBoundsException();

        }


        byte[] e = Arrays.copyOfRange(buf, head, wrapIndex(head + endIndex ));

        head = wrapIndex(head + endIndex + 1);

        if(readAllData){Log.v("unity","Buffer empty");

            PluginToUnity.ControlMessages.EMPTIED_DATA.send(id);
        }
        return e;

    }

    private  byte[] pollPacketArray(int endIndex) {

        int s = size();
        if (s == 0)  throw new IllegalStateException("Cann't poll Packet"
                + "Buffer is empty");

        if (endIndex < 0 || endIndex >= s ) {

            throw new IndexOutOfBoundsException();

        }


        byte[] e = Arrays.copyOfRange(buf, head, wrapIndex(head + endIndex));

        head = wrapIndex(head + endIndex + 1);

        return e;

    }

    public synchronized byte[] pollPacket(int id) {
        switch (mode){
            case LENGTH_PACKET :
                if(lengthPacketsCounter > 0) {

                    byte[] temp = pollPacketArray(lengthPacketsCounter -1);

                    if(lengthPacketsCounter <= 0)
                        PluginToUnity.ControlMessages.EMPTIED_DATA.send(id);
                }
                else return  null;


            case END_BYTE_PACKET :
                if (!marks.isEmpty()) {

                    byte[] temp = pollPacketArray(marks.poll());
                    if(marks.isEmpty())
                        PluginToUnity.ControlMessages.EMPTIED_DATA.send(id);

                } else return null;

            case NO_PACKETIZATION:
                byte[] temp = pollPacketArray(size());
                PluginToUnity.ControlMessages.EMPTIED_DATA.send(id);
                 return temp;
            default: return null;
        }

    }


}