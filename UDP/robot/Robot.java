package robot;

import java.net.*;
import java.io.*;

/**
 * TODO: nemá potvrzovací číslo v intervalu <seq - velikost okénka, seq> kde seq
 * pokud 20x za sebou posle prikaz FIN je sekvenční číslo příjemce
 *
 * @author Adam Plansky if you have some question please contact me:
 * plansada@fit.cvut.cz
 *
 *
 */
//It is DEVELOPER BRANCH
public class Robot {

    public static void main(String[] args) {
        if (args.length == 1) {
               try {
                Client c = new Client(args[0], 4000);
                c.receiveScreenshot();
            } catch (Exception e) {
                System.out.println(e);
            }
        } else if (args.length == 2) {
            try {
                Client c = new Client(args[0], 4000);
                c.sendFirmware(args[1]);
            } catch (Exception e) {
                System.out.println(e);
            }
        } else {
            System.out.println("please insert ip adress as a first parametr and if you want to upload firmware please insert firmware as a second parameter");
        }

    }
}

class Client {

    DatagramSocket socket;
    DatagramPacket packet;
    Send s;

    public Client(String address, int port) throws UnknownHostException, SocketException, FileNotFoundException {
        socket = new DatagramSocket();
        socket.setSoTimeout(100);
        s = new Send(socket, InetAddress.getByName(address), port);
    }

    public void sendFirmware(String pathToFile) throws IOException {
        System.out.println("FIRMWARE");
        s.firmware(pathToFile);
        System.out.println("FIRMWARE");
    }

    public void receiveScreenshot() throws IOException {
        System.out.println("SCREENSHOT");
        s.screenshot();
        System.out.println("SCREENSHOT");
    }
}
class Send {

    int idCon;
    int ack, seq;
    byte flag;
    byte[] data;
    int dataLen;
    //baryk
    final byte SYN = 1;
    final byte FIN = 2;
    final byte RST = 1;
    private byte mode = 0;
    byte[] message;
    private ByteArrayOutputStream baos;
    private DataOutputStream daos;
    private DatagramPacket packet;
    private InetAddress address;
    private int port;
    private DatagramSocket socket;
    private String firmwareString;
    private boolean connNumber;
    private Receive r;
    public Tmr t;
    private Window w;
    byte counterSamePacket;

    ////TIME
    public Send(DatagramSocket socket, InetAddress address, int port) throws FileNotFoundException {
        this.socket = socket;
        this.address = address;
        this.port = port;
        idCon = seq = 0;
        ack = 0;
        flag = SYN;
        data = new byte[1];
        connNumber = false;
        r = new Receive(socket, this);
        t = new Tmr();

    }

    public void screenshot() throws IOException {
        setMode(1);
        while (idCon == 0) {
            send();
            r.receive();
            //if(t.getElapsedTime1() > 2000)break;//RST;
            if (t.getElapsedTime1() > 2000) {
                break;
            }
        }
        while (flag == 0) {
            r.receive();
            send();
        }
        r.w.fos.close();
        if (r.w.end == true) {
            System.out.println("Prenaseni probehlo uspesne.");
        }
        System.out.println("ENDE1");
    }

    public void firmware(String pathToFile) throws IOException {
        this.firmwareString = pathToFile;
        setMode(2);
        while (idCon == 0) {
            sendF();
            r.receiveF();
            if (t.getElapsedTime1() > 2000) {
                break;
            }
        }
        if (flag == 0) {
            System.out.println("SPOJENI NAVAZANO ZACINAM ODESILAT PACKETY");
            sendAll();
        }
        while (flag == 0) {
            r.receiveF();
//            if (counterSamePacket == 3) {
//                sendAll();
//                counterSamePacket = 0;
//            } else {
            sendF();
            //}
        }
        if (r.flag == 0) {
            while (r.flag != FIN) {

                r.receiveF();
                sendF();
                if (flag != FIN) {
                    break;
                }
            }
        }
        r.w.fis.close();
        if (r.w.end == true && r.flag == FIN && flag == FIN) {
            System.out.println("Prenaseni probehlo uspesne.");
        }
    }

    public void send() throws IOException {
        buildMessage();
        packet = new DatagramPacket(message, message.length, address, port);
        socket.send(packet);
        dataLen = packet.getLength() - 9;
        print();
    }

    public void sendF() throws IOException {
        if (flag == 0) {
            data = w.dataInPacket();
        }
        buildMessageF();
        packet = new DatagramPacket(message, message.length, address, port);
        socket.send(packet);
        dataLen = packet.getLength() - 9;
        //  System.out.println("Packe SEND ma delku " + packet.getLength() + " data maji delku: " + dataLen);
        printF();
    }

    public void sendAll() throws IOException {
        for (int i = 0; i < 8; i++) {
            this.seq = w.getSeqOnIndex(i);
            data = w.dataInPacketAtIndex(i);
            buildMessageF();
            packet = new DatagramPacket(message, message.length, address, port);
            socket.send(packet);
            dataLen = packet.getLength() - 9;
            printF();
        }
    }

    public void setHead(int idCon, short seq, short ack, byte flag) {
        this.idCon = idCon;
        this.seq = (seq & 0xFFFF);
        this.ack = (ack & 0xFFFF);
        this.flag = flag;
    }

    private void buildMessage() throws IOException {
        baos = new ByteArrayOutputStream();
        daos = new DataOutputStream(baos);
        daos.writeInt(idCon);
        daos.writeShort((short) seq);
        daos.writeShort((short) ack);
        daos.writeByte(flag);
        //download screenshort
        if (mode == 1) {
            if (flag == SYN) {
                daos.write(data, 0, data.length);
            }
        } else if (mode == 2) {
            if (flag == SYN) {
                daos.write(data, 0, data.length);
            }
        }
        message = baos.toByteArray();
        daos.close();
        baos.close();
    }
    //for F = for firmware

    private void buildMessageF() throws IOException {
        baos = new ByteArrayOutputStream();
        daos = new DataOutputStream(baos);
        daos.writeInt(idCon);
        daos.writeShort(seq);
        daos.writeShort((short) ack);
        daos.writeByte(flag);
        //download screenshort

        if (flag == SYN) {
            daos.write(data, 0, data.length);
        } //else if (flag == FIN) {}
        else if (flag == FIN) {
        } else {
            if (w.end == true && w.endSeq - w.endDatLen == seq) {
                daos.write(data, 0, w.endDatLen);
            } else {
                daos.write(data, 0, data.length);
            }
        }
        message = baos.toByteArray();
        daos.close();
        baos.close();
    }

    public void setMode(int mode) throws FileNotFoundException, IOException {
        this.mode = (byte) mode;
        data[0] = this.mode;
        if (mode == 1) {
            w = new Window();
        } else if (mode == 2) {
            w = new Window(firmwareString);
        }
        r.setWindow(w);

    }

    public int getMode() {
        System.out.println(String.format("%02X ", mode));
        return mode;
    }

    public void print() {
        System.out.print(t.getElapsedTime1() + " " + (String.format("%8s", Integer.toHexString(idCon))).replace(' ', '0') + " SEND seq=" + seq + " ack=" + ack + " flag=" + flag + " data(" + dataLen + "): ");
        StringBuilder sb = new StringBuilder();

        if (flag == SYN) {
            for (byte b : data) {
                sb.append(String.format("%02X ", b));
            }
        } else {
            System.out.println("--");
        }
        System.out.println(sb.toString());
    }

    public void printF() {
        System.out.print(t.getElapsedTime1() + " " + (String.format("%8s", Integer.toHexString(idCon))).replace(' ', '0') + " SEND seq=" + seq + " ack=" + ack + " flag=" + flag + " data(" + dataLen + "): ");
        StringBuilder sb = new StringBuilder();

        if (flag == SYN) {
            for (byte b : data) {
                sb.append(String.format("%02X ", b));
            }
        } else if (flag == 0) {
            for (int i = 0; i < dataLen; i++) {
                sb.append(String.format("%02X ", data[i]));
            }
        }
        System.out.println(sb.toString());
    }

    public void getHead() {
    }

    public int getIdCon() {
        return idCon;
    }

    public int getSeq() {
        return seq;
    }

    public int getAck() {
        return ack;
    }

    public byte getFlag() {
        return flag;
    }

    public String getFirmwareString() {
        return firmwareString;
    }
}
class Receive {

    int idCon;
    int seq, ack;
    byte flag;
    byte[] data;
    int dataLen;
    //baryk
    //final byte SYN = 1;
    final byte SYN = 1;
    final byte FIN = 2;
    final byte RST = 1;
    final int DOWNLOAD = 0x01;
    final int UPLOAD = 0x02;
    byte[] message;
    private ByteArrayInputStream bais;
    private DataInputStream dais;
    private DatagramSocket socket;
    private DatagramPacket packet;
    private Send s;
    private int seq1;
    private int ack1;
    private static int counter = 0;
    int lastPacket;
    Window w;

    public Receive(DatagramSocket socket, Send s) throws FileNotFoundException {
        this.socket = socket;
        data = new byte[255];
        message = new byte[255 + 9];
        this.s = s;
        seq1 = 0;
        counter = 0;
        flag = -1;
        lastPacket = 0;
    }

    public void setWindow(Window w) {
        this.w = w;
    }

    public void receive() throws IOException {

        try {
            packet = new DatagramPacket(message, message.length);
            socket.receive(packet);
            dataLen = packet.getLength() - 9; //buffer for data - head of packet
            convertFromPacket();
            print();
            if (s.idCon == idCon) {
                //if it is correct packet
                if (s.flag == 0 && flag == 0) {
                    //received packet is packet what i needed
                    doAction();
                } else if (flag == FIN && dataLen > 0) {
                    //System.out.println("RST FLAG");
                } else if (flag == FIN) {
                    System.out.println("ENDE ");
                    s.setHead(idCon, (short) 0, (short) (seq1), FIN);
                }
            } else {
                if (flag == SYN && s.flag == SYN && s.getIdCon() == 0 && dataLen == 1 && ((data[0] == 1) || data[0] == 2)) {
                    setSendPacket();
                    lastPacket = ack;
                } else if (s.getFlag() == SYN && flag == 0) {
                    //System.out.println("packet zahazuju");
                } else {
                    //System.out.println("RST IDCON MISSING");
                }
            }
            if (flag > 4 || s.flag == 3);//RST

        } catch (SocketTimeoutException e) {
            System.out.println("RCV TIMEOUT");
            counter++;
            if (counter >= 20) {
                s.setHead(idCon, (short) seq, (short) ack, RST);
                System.out.println("Posilam RST packet");
            } else {
                s.send();
                receive();
            }

        }
        counter = 0;
    }
    //this method receive Firmware no bytes are allowed

    public void receiveF() throws IOException {
        try {
            packet = new DatagramPacket(message, message.length);
            socket.receive(packet);
            dataLen = packet.getLength() - 9; //buffer for data - head of packet
            convertFromPacket();
            print();
            if (s.idCon == idCon) {
                if (s.flag == 0 && flag == 0) {
                    //received some good packet
                    doActionF();
                } else if (flag == FIN) {
                    System.out.println("ENDE ");
                }
            } else {
                if (flag == SYN && s.flag == SYN && s.getIdCon() == 0 && dataLen == 1 && ((data[0] == 1) || data[0] == 2)) {
                    setSendPacket();
                } else if (s.getFlag() == SYN && flag == 0) {
                    //System.out.println("packet zahazuju");
                } else {
                    //System.out.println("RST IDCON MISSING");
                }
            }

        } catch (SocketTimeoutException e) {
            //System.out.println("TIMEOUT");
            counter++;
            if (counter >= 20) {
                s.setHead(idCon, (short) seq, (short) ack, RST);
                System.out.println("Posilam RST packet");
            } else {
                if (flag == 0) {
                    s.sendAll();
                    receiveF();
                } else {
                    s.sendF();
                    receiveF();
                }
            }

        }
        counter = 0;
    }

    void doAction() throws IOException {

        if (seq == s.ack) {
            seq1 = w.next(data, seq, dataLen);
            if (seq1 >= 65536) {
                seq1 %= 65536;
                seq %= 65536;
            }
            s.setHead(idCon, (short) 0, (short) (seq1), flag);
        } else if (seq - s.ack < 2040 && seq - s.ack >= 0)//i needed remember this packet
        {
            System.out.println("tento packet si budu pamatovat");
            System.out.println("dataLen = " + dataLen);
            w.Add(data, seq, dataLen);
        } else if (seq < 2040 && (65536 - (seq + s.ack)) < 2040 && (65536 - (seq + s.ack)) > -2040 && s.ack > seq) {
            System.out.println("tento packet si budu pamatovat PRETEJKAM");
            System.out.println("dataLen = " + dataLen);
            w.Add(data, seq, dataLen);
        }
    }

    void doActionF() throws IOException {
        if (ack == lastPacket) {
            s.counterSamePacket++;
        } else if (w.end == true && ack>=w.endSeq) {
            System.out.println("NASTAVUJU FIN");
            System.out.println("ACK = " + ack + ", endSeq = " + w.endSeq);
            s.setHead(idCon, (short) ack, (short) 0, FIN);
        } else {
            ack1 = w.addNextPackets(ack);
            System.out.println("################");
            System.out.println("ACK = " + ack1);
            System.out.println("################");
            s.setHead(idCon, (short) ack1, (short) 0, flag);

        }
    }

    void convertFromPacket() throws IOException {
        bais = new ByteArrayInputStream(message);
        dais = new DataInputStream(bais);
        idCon = dais.readInt();
        seq = (dais.readShort() & 0xFFFF);
        ack = (dais.readShort() & 0xFFFF);
        flag = dais.readByte();
        dais.read(data);
        dais.close();
        bais.close();
    }

    void setSendPacket() {
        if (s.getIdCon() == 0) {
            if (flag == SYN) {
                //prijmul sem packet ktery sem potreboval muzu zacit prijmat data
                //nastav send na id con
                s.setHead(idCon, (short) seq, (short) ack, (byte) 0);
            }
        }
    }

    public void print() {

        System.out.print(s.t.getElapsedTime1() + " " + (String.format("%8s", Integer.toHexString(idCon))).replace(' ', '0') + " RECV seq=" + (seq & 0xFFFF) + " ack=" + (ack & 0xFFFF) + " flag=" + flag + " data(" + dataLen + "): ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dataLen; i++) {
            sb.append(String.format("%02X ", data[i]));
        }
        System.out.println(sb.toString());
    }

    public int getIdCon() {
        return idCon;
    }

    public short getSeq() {
        return (short) seq;
    }

    public int getAck() {
        return ack;
    }

    public byte getFlag() {
        return flag;
    }
}
class Window {

    int top = 7;
    int seqTop = 0;
    final int sw = 255;
    byte[] w;
    int[] s;
    boolean end = false;
    int endDatLen = -10;
    int endSeq = 0;
    private int mode;
    FileOutputStream fos;
    FileInputStream fis;
    private int idx = -1;
    private int cntSamePacket = 3;
    int i1 = 0, i2 = 0, i3 = 0, i4 = 0;

    public Window() throws FileNotFoundException {
        w = new byte[2040];
        s = new int[8];
        this.mode = 1;
        fos = new FileOutputStream("foto.png");
    }

    public Window(String firmwarePathTofile) throws FileNotFoundException, IOException {
        w = new byte[2040];
        s = new int[8];
        this.mode = 2;
        System.out.println("OK FIRMWAR");
        fis = new FileInputStream(firmwarePathTofile);
        initLoadFirmware();
    }

    public void initLoadFirmware() throws IOException {
        int seqNumber = 0;
        for (int i = 0; i < 8; i++) {
            int idx = getIdx(seqNumber);
            s[idx] = seqNumber;
            fis.read(w, idx * 255, 255);
            /////////////////////
//            fos.write(w, idx * 255, 255);
            seqNumber += 255;
        }
        seqTop = 0;
    }

    public byte[] dataInPacket() {
        byte[] data = new byte[255];
        if (end == true && seqTop == endSeq) {
            System.arraycopy(w, seqTop*255, data, 0, endDatLen);
        } else {
            System.arraycopy(w, seqTop*255, data, 0, 255);
        }
        //Print();
        return data;
    }

    public byte[] dataInPacketAtIndex(int idx) {
        byte[] data = new byte[255];
        if (end == true && idx * 255 == endSeq) {
            System.arraycopy(w, idx * 255, data, 0, endDatLen);
        } else {
            System.arraycopy(w, idx * 255, data, 0, 255);
        }
        //Print();
        return data;
    }

    public int addNextPackets(int ack) throws IOException {
       
        Print();
        if (ack != s[seqTop]) {
            int ret = 0;
            if (end == false) {
                while (s[seqTop] != ack) {
                    if (end == true) {
                        seqTop = ++seqTop % 8;
                        continue;
                    }
                    ret = fis.read(w, seqTop * 255, 255);
                    if (ret != 255) {
                        //System.out.print("NACITAM POSLEDNI BYTE");
                        //Print();
                        if (ret == -1) {
                            s[seqTop] = (s[seqTop] + 2040) % 65536;
                            endSeq = s[seqTop];
                            end = true;
                            if (endDatLen <= 0) {
                                endDatLen = 255;
                            }
                        } else {
                            //System.out.println(s[seqTop]);
                            s[seqTop] = (s[seqTop] + 2040) % 65536;
                            
                            endDatLen = ret;
                            endSeq = s[seqTop] + ret;
                            ///////////////////////////////////
//                            fos.write(w, seqTop * 255, endDatLen);
//                            fos.close();
                            end = true;
                            int mm = seqTop;
                            for(; ; ){
                                System.out.println(s[mm++]);
                                if(mm==8)break;
                            }
                        }
                       // System.out.println(endSeq);
                    } else {
                       // fos.write(w, seqTop * 255, 255);
                        System.out.println(s[seqTop]);
                        s[seqTop] = (s[seqTop] + 2040) % 65536;
                    }
                    seqTop = getIdx(seqTop - 1);
                }
            }
        } else {
            while (s[seqTop] != ack) {
                System.out.println(s[seqTop]);
                ///////////////////////////////////
                fos.write(w, seqTop * 255, 255);
                seqTop = getIdx(seqTop - 1);
            }
        }
        Print();
        return s[seqTop];
    }

    public int getSeqOnIndex(int idx) {
        return s[idx];
    }

    boolean send8Packets() {
        if (cntSamePacket == 3) {
            return true;
        }
        return false;
    }
    //return packet I need

    public void Add(byte[] data, int seqNumber, int dataLen) throws IOException {

        int idx = getIdx(seqNumber);
        if (s[idx] == seqNumber && seqNumber != 0) {
            return;
        }
        s[idx] = seqNumber;
        System.arraycopy(data, 0, w, idx * 255, data.length);

        if (dataLen < 255) {
            end = true;
            endDatLen = dataLen;
            endSeq = seqNumber;
        }
        Print();
    }

    public int next(byte[] data, int seqNumber, int dataLen) throws IOException {
        System.out.print("seqNumber = " + seqNumber);
        Add(data, seqNumber, dataLen);
        int temp, temp2, start = 0;

        do {
            temp = s[seqTop] % 65536;
            toFile();
            --seqTop;
            ++start;
            seqTop = getIdx(seqTop);
            temp2 = (s[seqTop] % 65536);
            System.out.println("temp = " + temp + ", temp2 = " + temp2);
        } while ((temp2 > temp && (temp2 - temp) < 2040) || ((temp > temp2) && 65536 - (temp - temp2) < 2040));

        System.out.println(" | seqTop= " + seqTop);
        if (end == true && endSeq == s[getIdx(seqTop + 1)]) {
            fos.close();
            return endSeq + endDatLen;

        } else {
            System.out.println("ASD: " + i1 + "," + i2 + "," + i3 + "," + i4 + ",");
            if (temp - temp2 > 30000) {
                i1++;
                return temp2 + 255;
            } else if (temp > temp2) {
                i2++;
                return temp + 255;
            } else if (temp2 - temp > 30000) {
                i3++;
                return temp + 255;
            } else {
                i4++;
                return temp2 + 255;
            }
        }
    }

    private void toFile() throws IOException {
        if (end == true && endSeq == s[seqTop]) {
            fos.write(w, seqTop * 255, endDatLen);
        } else {
            fos.write(w, seqTop * 255, 255);
        }
        System.out.println("################## : " + s[seqTop]);
    }

    public void Print() {
        System.out.print("[ ");
        for (int i = 0; i < 8; i++) {
            System.out.print(s[i] + ", ");
        }
        System.out.println(" ]");
    }

    //get idx in ranged 0-7 for ack/seq
    public int getIdx(int number) {
        if (number < 1) {
            int a = (number) % 8;
            while (a < 0) {
                a += 8;
            }
            return a;
        } else {
            return (number) % 8;
        }
    }
}
class Tmr {

    public long startTime;

    public Tmr() {
        this.startTime = System.currentTimeMillis();
    }

    public long getElapsedTime1() {
        long elapsed;
        elapsed = (System.currentTimeMillis() - startTime);
        return elapsed;
    }
}

