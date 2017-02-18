package com.pedro.rtsp.rtp;

import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import android.os.SystemClock;
import java.util.Random;

/**
 * Implementation of Sender Report RTCP packets.
 */
public class SenderReport {

  private static final int MTU = 1500;
  private static final int PACKET_LENGTH = 28;

  private MulticastSocket usock;
  private DatagramPacket upack;

  private byte[] mBuffer = new byte[MTU];
  private int mSSRC, mPort = -1;
  private int mOctetCount = 0, mPacketCount = 0;
  private long interval, delta, now, oldnow;

  public SenderReport() {
    setSSRC(new Random().nextInt());
    /*							     Version(2)  Padding(0)					 					*/
    /*									 ^		  ^			PT = 0	    						*/
		/*									 |		  |				^								*/
		/*									 | --------			 	|								*/
		/*									 | |---------------------								*/
		/*									 | ||													*/
		/*									 | ||													*/
    mBuffer[0] = (byte) Integer.parseInt("10000000", 2);

		/* Packet Type PT */
    mBuffer[1] = (byte) 200;

		/* Byte 2,3          ->  Length		                     */
    setLong(PACKET_LENGTH / 4 - 1, 2, 4);

		/* Byte 4,5,6,7      ->  SSRC                            */
		/* Byte 8,9,10,11    ->  NTP timestamp hb				 */
		/* Byte 12,13,14,15  ->  NTP timestamp lb				 */
		/* Byte 16,17,18,19  ->  RTP timestamp		             */
		/* Byte 20,21,22,23  ->  packet count				 	 */
		/* Byte 24,25,26,27  ->  octet count			         */

    try {
      usock = new MulticastSocket();
    } catch (IOException e) {
      // Very unlikely to happen. Means that all UDP ports are already being used
      throw new RuntimeException(e.getMessage());
    }
    upack = new DatagramPacket(mBuffer, 1);

    // By default we sent one report every 3 second
    interval = 3000;
  }

  public void close() {
    usock.close();
  }

  /**
   * Sets the temporal interval between two RTCP Sender Reports.
   * Default interval is set to 3 seconds.
   * Set 0 to disable RTCP.
   *
   * @param interval The interval in milliseconds
   */
  public void setInterval(long interval) {
    this.interval = interval;
  }

  /**
   * Updates the number of packets sent, and the total amount of data sent.
   *
   * @param length The length of the packet
   * @param rtpts The RTP timestamp.
   * @throws IOException
   **/
  public void update(int length, long rtpts, int port){
    mPacketCount += 1;
    mOctetCount += length;
    setLong(mPacketCount, 20, 24);
    setLong(mOctetCount, 24, 28);

    now = SystemClock.elapsedRealtime();
    delta += oldnow != 0 ? now - oldnow : 0;
    oldnow = now;
    if (interval > 0) {
      if (delta >= interval) {
        // We send a Sender Report
        send(System.nanoTime(), rtpts, port);
        delta = 0;
      }
    }
  }

  public void setSSRC(int ssrc) {
    this.mSSRC = ssrc;
    setLong(ssrc, 4, 8);
    mPacketCount = 0;
    mOctetCount = 0;
    setLong(mPacketCount, 20, 24);
    setLong(mOctetCount, 24, 28);
  }

  public void setDestination(InetAddress dest, int dport) {
    mPort = dport;
    upack.setPort(dport);
    upack.setAddress(dest);
  }

  public int getPort() {
    return mPort;
  }

  public int getLocalPort() {
    return usock.getLocalPort();
  }

  public int getSSRC() {
    return mSSRC;
  }

  /**
   * Resets the reports (total number of bytes sent, number of packets sent, etc.)
   */
  public void reset() {
    mPacketCount = 0;
    mOctetCount = 0;
    setLong(mPacketCount, 20, 24);
    setLong(mOctetCount, 24, 28);
    delta = now = oldnow = 0;
  }

  private void setLong(long n, int begin, int end) {
    for (end--; end >= begin; end--) {
      mBuffer[end] = (byte) (n % 256);
      n >>= 8;
    }
  }

  /**
   * Sends the RTCP packet over the network.
   *
   * @param ntpts the NTP timestamp.
   * @param rtpts the RTP timestamp.
   */
  private void send(final long ntpts, final long rtpts, final int port){
    new Thread(new Runnable() {
      @Override
      public void run() {
        long hb = ntpts / 1000000000;
        long lb = ((ntpts - hb * 1000000000) * 4294967296L) / 1000000000;
        setLong(hb, 8, 12);
        setLong(lb, 12, 16);
        setLong(rtpts, 16, 20);
        upack.setLength(PACKET_LENGTH);
        upack.setPort(port);
        Log.i("Pedro", "send packet from senderreport Port" + upack.getPort());
        try {
          usock.send(upack);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }).start();
  }
}
