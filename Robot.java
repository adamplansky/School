package robot;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Random;
import java.util.regex.*;

/**
 *
 * @author Adam Plansky if you have some question please contact me:
 * plansada@fit.cvut.cz
 *
 *
 */
//TODO IP ADRESA + PORT V PRIKAZOVE RADCE
//TODO : REKURZE PRI OSLOVENI
//TOOD : SERVER POSLE SPATNY SOURADNICE
//TODO : KLIENT MUSI BYT STABILNI PROTI NEOCEKAVANYM VSTUPUM???
public class Robot {

    public static void main(String[] args) throws IOException {
        if (args.length == 2) {
            String textToserver;
            Client c = new Client();
            Board b = new Board(c);
            try {
                c.openConnection(args[0], Integer.parseInt(args[1]));


                while (c.getSocket().isConnected()) {
                    String actText = c.readNextLine();
                    textToserver = b.getText(actText);

                    if (textToserver.equals("end")) {
                        break;
                    }
                    c.sendToServer(textToserver);
                }
            } catch (Exception e) {
                System.out.println(e);
            } finally {
                try {
                    c.closeConnection();
                } catch (Exception e) {

                    System.out.println(e);

                }
            }
        } else if (args.length == 1) {
            ServerSocket serverSocket = null;
            boolean listening = true;

            try {
                serverSocket = new ServerSocket(Integer.parseInt(args[0]));
            } catch (IOException e) {
                System.err.println("Could not listen on port: " + args[0]);
                System.exit(-1);
            }
            while (listening) {
                Socket clientSocket = serverSocket.accept();

                new Thread(new Server(clientSocket)).start();
            }

            serverSocket.close();

        } else {
            System.out.println("No parametrs");
        }

    }
}

class Server implements Runnable {

    private PrintWriter out = null;
    private BufferedReader in = null;
    private final Socket clientSocket;
    private Playground pg = null;
    private char[] msg;
    private int buffLen;
    private String inputText;
    private boolean r = false;

    public Server(Socket socket) throws IOException {
        clientSocket = socket;

        pg = new Playground();
        buffLen = pg.getMaxLength();
        msg = new char[buffLen];
    }

    private void openConnection() throws IOException {
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    private void closeConnection() throws IOException {

        out.close();
        in.close();
        clientSocket.close();

    }

    void send(String msg) {
        System.out.println(msg);
        out.print(msg + "\r\n");
        out.flush();
    }

    @Override
    public void run() {
        try {
            openConnection();
            //send 210 
            send(pg.firstMessage());
            int value;
            StringBuilder sb = new StringBuilder();
            String msgToClient;
            while ((value = in.read()) != -1) {
                sb.append((char) value);

                //it always ends with \r\n
                if ((sb.charAt(sb.length() - 1) == '\n' && sb.charAt(sb.length() - 2) == '\r')) {
                    msgToClient = pg.getClientText(sb.toString());
                    send(msgToClient);
                    sb = new StringBuilder();
                }
                if (clientSocket.isClosed()) {
                    break;
                }
                if (pg.isEnd()) {
                    break;
                }
            }



        } catch (IOException e) {
            System.out.println("server + run.catch - " + e);
        } finally {
            try {
                closeConnection();
            } catch (IOException e) {
                System.out.println("server + run.finally - " + e);
            }
        }
    }
}

class Playground {

    private String names[] = {"Karle", "Matko Prirodo", "Kazisvete", "Troubo domaci", "Lenochu", "Max"};
    private Random rnd = new Random();
    private int number;
    private int maxLength; //12 =_OPRAVIT N\r\n = longest possible string
    private String name;
    private int x;
    private int y;
    private String orientations[] = {"L", "D", "R", "U"};
    private int orientation;
    private boolean crashedCPU = false;
    private int noCrashedCPU = -1;
    private int stepCounter = 0;
    private boolean end = false;
    private String text;
    //treasure is on the 

    public Playground() {
        number = rnd.nextInt(names.length);
        number = 5;
        System.out.println(names[number]);
        name = names[number]; //get random name
        maxLength = names[number].length() + 12;
        this.x = rnd.nextInt(35) - 17; //get random coordination to robot
        this.y = rnd.nextInt(35) - 17; //get random coordination to robot
        this.orientation = rnd.nextInt(4); //get random direction to robot
    }

    public String getClientText(String text) {
        this.text = text;
        return generateMsg();
    }

    private boolean checkName() {
        Pattern pattern = Pattern.compile("^" + name);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return true;
        }
        return false;
    }

    private boolean checkEnding() {
        if (text.length() < 2) {
            return false;
        }
        if (text.charAt(text.length() - 1) == '\n' && text.charAt(text.length() - 2) == '\r') {
            return true;
        }
        return false;
    }
    //remove and and \r\n from inputText

    private void removeEndName() {
        text = text.replaceFirst(name + " ", "").replaceFirst("\r\n", "");
    }

    private String generateMsg() {
        if (checkEnding() && checkName()) {
            removeEndName();
            if (this.text.equals("KROK")) {
                if (crashedCPU == true) {
                    return get572();
                }
                stepCounter++;
                //robot must repair itself every 10 step
                //or just some random 
                if (stepCounter == 10 || rnd.nextInt(20) == 0) {
                    stepCounter = 0;
                    return get580();
                }
                setCord();
                if (!checkCord()) {
                    return get530();
                }
                return get240();
            } else if (this.text.equals("VLEVO")) {
                orientation++;
                if (orientation == 4) {
                    orientation = 0;
                }
                return get240();
            } else if (this.text.equals("ZVEDNI")) {
                if (this.x == 0 && this.y == 0) {
                    return get260();
                } else {
                    return get550();
                }
            } else if (text.contains("OPRAVIT ") && text.length() == 9) {
                int cpu = Character.getNumericValue(text.charAt(8));
                if (cpu == noCrashedCPU) {
                    crashedCPU = false;
                    noCrashedCPU = -1;
                    return get240();
                } else if (cpu > 9 || cpu < 1) {
                    return get500();
                }

                return get571();
            }
        }
        return get500();

    }
    //if robot is in the city

    private boolean checkCord() {
        if (x > 18 || x < -18 || y > 18 || y < -18) {
            return false;
        }
        return true;
    }

    public boolean isEnd() {
        return end;
    }

    private String get500() {
        return "500 NEZNAMY PRIKAZ";
    }

    private String get240() {
        return "240 OK (" + this.x + "," + this.y + ")";
    }

    private String get260() {
        end = true;
        return "260 USPECH poklad je prazdny.. byl si moc pomaly";
    }

    private String get530() {
        end = true;
        return "530 HAVARIE";
    }

    private String get550() {
        end = true;
        return "550 NELZE ZVEDNOUT ZNACKU";
    }

    private String get571() {
        end = true;
        return "571 PROCESOR FUNGUJE";
    }

    private String get572() {
        end = true;
        return "572 ROBOT SE ROZPADL";
    }

    private String get580() {
        noCrashedCPU = rnd.nextInt(9) + 1;
        crashedCPU = true;
        return "580 SELHANI PROCESORU " + noCrashedCPU;
    }

    void setCord() {
        switch (orientations[orientation]) {
            case "U":
                y++;
                break;
            case "D":
                y--;
                break;
            case "L":
                x--;
                break;
            case "R":
                x++;
                break;
        }
    }

    public int getMaxLength() {
        return maxLength;
    }

    public String firstMessage() {
        return "210 Ahoj tady je robot verze 1.8.\n Oslovuj mne " + name + ".";


    }
}
//java robot.Robot &
//class which is navigating robot
//class which can get the name from the 210 command
class Client {

    private Socket socket = null;
    private PrintWriter out = null;
    private BufferedReader in = null;

    public Socket getSocket() {
        return socket;
    }

    //OK open connection
    public void openConnection(String ipAddress, int port) throws Exception {
        socket = new Socket(ipAddress, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    //OK close connection
    public void closeConnection() throws Exception {
        in.close();
        out.close();
        socket.close();
    }

    //read next line from server
    String readNextLine() throws IOException {
        String text = in.readLine();
        System.out.println("SERVER: " + text);
        return text;
    }

    //send message to server ... for example name VLEVO
    void sendToServer(String msg) {
        System.out.print(msg + "\r\n");
        out.print(msg + "\r\n");
        out.flush();
    }
}

//class which is navigating robot
//class which can get the name from the 210 command
class Board {

    private String inputText;
    private String name;
    private int x;
    private int y;
    private int cmdNumber = -1;
    private String orientation = "UNDEFINED";
    Pattern pattern = Pattern.compile("^(210)|(240)|(260)|(500)|(530)|(550)|(571)|(572)|(580)");
    private Client robot = null;

    Board(Client robot) {
        this.robot = robot;
        this.name = "";
        this.x = -30; //some random value which is out of the city
        this.y = -30;
    }

    //server send us some text and in this method it is processed
    public String getText(String text) {
        inputText = text;
        cmdNumber(); //set the cmdNumber
        return getCmd();
    }

    //get the command number
    private void cmdNumber() {
        Matcher matcher = pattern.matcher(inputText);
        if (matcher.find()) {
            cmdNumber = Integer.parseInt(matcher.group());
        } else {
            cmdNumber = -1;
        }
    }

    //get from the cmdNumber the text what we want to send to server
    private String getCmd() {
        if (cmdNumber == 210) {
            loadName();
            return get210();
        } else if (cmdNumber == 240) {
            setXY();
            return get240();
        } else if (cmdNumber == 580) {
            return get580();
        } else if (cmdNumber == 260) {
            return "end";
        } else {
            //return get240();
            return "neznam prikaz";
        }
    }

    private String get210() {
        return name + " VLEVO";
    }

    private String get240() {
        return name + nextStep();
    }

    private String get580() {
        return name + " OPRAVIT " + getWrongCPU();
    }

    private void loadName() {
        Addressed adr = new Addressed();
        while (name.equals("")) {
            try {
                while (name.equals("")) {
                    name = adr.getAdd(inputText);
                    if (name.equals("")) {
                        inputText = robot.readNextLine();
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

    //if robot has crashed CPU it return number of wrong CPU
    private int getWrongCPU() {
        String s = inputText;
        s = s.substring(s.lastIndexOf(' ') + 1);
        return Integer.parseInt(s);
    }

    //first get x to 0 than y to 0
    private String nextStep() {
        if ("UNDEFINED".equals(this.orientation)) {
            return " KROK";
        }
        if (this.x < 0 && !this.orientation.equals("R")) {
            turnRobot();
            return " VLEVO";
        } else if (this.x > 0 && !"L".equals(this.orientation)) {
            turnRobot();
            return " VLEVO";
        } else if (this.x != 0) {
            return " KROK";
        } else if (this.y < 0 && !"U".equals(this.orientation)) {
            turnRobot();
            return " VLEVO";
        } else if (this.y > 0 && !"D".equals(this.orientation)) {
            turnRobot();
            return " VLEVO";
        } else if (this.y != 0) {
            return " KROK";
        } else if (this.y == 0 && this.x == 0) {
            return " ZVEDNI";
        }
        return "240 neco se pokazilo ... nepokryl sem vsechny moznosti";
    }

    //turn robot to left ... from UP to LEFT etc..
    private void turnRobot() {
        switch (this.orientation) {
            case "R":
                this.orientation = "U";
                break;
            case "U":
                this.orientation = "L";
                break;
            case "L":
                this.orientation = "D";
                break;
            case "D":
                this.orientation = "R";
                break;
        }
    }

    // set X and Y coordination of robot + orientation
    private void setXY() {
        String xy = inputText;
        if (x == -30) {
            setCoordination(xy);
            //dostal sem souradnice ale ted potrebuju udelat krok abych zjistil na jakou stranu je robot natocen
        } else {
            if (this.orientation.equals("UNDEFINED")) {
                getOrientation(xy); //it get orientation of robot + setCoordination of actual setp  
                //get the orientation .. i have last coordination + actual coordination
            } else { //i have orientation
                setCoordination(xy);
            }


        }
    }

    //if i dont know orientation of robot
    //this metod get me the orientation of robot
    private void getOrientation(String xy) {
        int xTemp = this.x;
        int yTemp = this.y;

        setCoordination(xy);

        int diffX = xTemp - this.x;
        int diffY = yTemp - this.y;

        if (diffX == 0) {
        } else if (diffX == -1) {
            this.orientation = "R"; //left
            return;
        } else if (diffX == 1) {
            this.orientation = "L"; //right
            return;
        } else {
            System.out.println("CHYBNY SMER V getOrientation");
        }

        if (diffY == 0) {
        } else if (diffY == -1) {
            this.orientation = "U"; //down
        } else if (diffY == 1) {
            this.orientation = "D"; //up
        } else {
            System.out.println("CHYBNY SMER V getOrientation");
        }

    }
    //set x and y coordination of robot

    private void setCoordination(String xy) {
        String xyText = xy.replace("240 OK (", "").replace(")", "");

        int i = xyText.indexOf(',');
        int len = xyText.length();

        String yString = xyText.subSequence(i + 1, len).toString();
        String xString = xyText.subSequence(0, i).toString();

        this.y = Integer.parseInt(yString);
        this.x = Integer.parseInt(xString);
    }
}

//class which can get the name from the 210 command
class Addressed {

    //jmeno je vse krome \r, \n, \0 a tečky + mezera nesmi byt na zacatku ani na konci jmena
    Pattern pattern = Pattern.compile("Oslovuj mne [^\\.\\r\\n\\00 ]{1}([^.\\r\\n\\00]*[^\\.\\r\\n\\00 ])?\\.");

    //return the name of the robot .. it the text doesnt have "Oslovuj mne xxx."
    //return empty string
    public String getAdd(String text) {
        return findPatern(text);
    }

    private String findPatern(String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group().replace("Oslovuj mne ", "").replace(".", "");
        }
        return "";

    }
}