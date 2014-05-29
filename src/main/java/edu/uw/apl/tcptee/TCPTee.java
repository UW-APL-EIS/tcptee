package edu.uw.apl.tcptee;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.commons.cli.*;

/**
 * @author Stuart Maclean
 *
 * Act as middle man (proxy?) in between a TCP client to server
 * connection.  The client is pointed at us, and we connect to the server.
 * So what was e.g.
 *
 * Firefox -> google.com:80 -> Google
 *
 * is now
 *
 * Firefox -> localhost:8080 -> TCPTee 8080 google.com 80 -> Google
 *
 * TCPTee can show ANY tcp data, NOT limited to http. In fact it is
 * content agnostic (other than being told it is ascii or binary data,
 * see below).
 *
 * Obviously not as powerful as e.g. Wireshark, tcpdump, since it requires
 * active participation by the client, e.g. Firefox in the above example.
 * But much easier to use and gives a friendly output.
 *
 * We monitor traffic in both directions.  Currently we just print to
 * stdout, and know nothing of the traffic semantics.  More elaborate
 * processing could be protocol-aware.
 *
 * Single option is '-b', denoting that the TCP payload is binary.
 * Then, any console output converts non-printable chars to '.', much
 * like xxd.  Note that this is ONLY for the tee'd output, the data
 * flow between client and server is untouched!
 *
 * Based loosely on the GUI tool 'TCPMonitor' (ws.apache.org/tcpmon)
 * but stripped down to a basic cmd-line UI.  Named after the Unix
 * 'tee' command.
 *
 * The print out has some 'context':
 *
 * C 562 - means 'client sent us 562 bytes', which we then forward to
 * the real server and print out.
 *
 * S 128 - means 'server sent us 128 bytes', which we then forward to
 * the real client and print out.
 */
   
public class TCPTee {

	static private void printUsage( Options os, String usage,
									String header, String footer ) {
		HelpFormatter hf = new HelpFormatter();
		hf.setWidth( 80 );
		hf.printHelp( usage, header, os, footer );
	}

	static public void main( String[] args ) {

		Options os = new Options();
		os.addOption( "b", false,
					  "Traffic is binary, so convert non-printable chars " +
					  "to '.' when showing traffic." );

		// much like ssh's -L option: 'port host hostport'
		final String USAGE = "[-b] listenPort connectHost connectPort";
		final String HEADER = "";
		final String FOOTER = "";
		
		CommandLineParser clp = new PosixParser();
		CommandLine cl = null;
		try {
			cl = clp.parse( os, args );
		} catch( Exception e ) {
			System.err.println( e );
			printUsage( os, USAGE, HEADER, FOOTER );
			System.exit(1);
		}
		
		boolean binaryData = cl.hasOption( "b" );
		args = cl.getArgs();
		if( args.length < 3 ) {
			printUsage( os, USAGE, HEADER, FOOTER );
			System.exit(1);
		}

		// Post options, cmd line processing...
		int listenPort = -1;
		String connectHost = args[1];
		int connectPort = -1;

		try {
			listenPort = Integer.parseInt( args[0] );
			connectPort = Integer.parseInt( args[2] );
		} catch( NumberFormatException nfe ) {
			printUsage( os, USAGE, HEADER, FOOTER );
			System.exit(1);
		}
			
		ServerSocket ss = null;
		try {
			ss = new ServerSocket( listenPort );
		} catch( IOException ioe ) {
			// If we can't listen, we are not going to be much use...
			System.err.println( ioe );
			System.exit(1);
		}
		
		System.out.println( "Listening: " + ss );
		while( true ) {
			try {
				Socket s = ss.accept();
				System.out.println( "Accepted : " + s );
				Worker w = new Worker( s, connectHost, connectPort,
									   binaryData );
				// Spawn new thread so we can go back to listening...
				new Thread( w ).start();
			} catch( Exception e ) {
				System.err.println( e );
			}
		}
	}

	static class Worker implements Runnable {
		Worker( Socket client, String serverHost, int serverPort,
				boolean binaryData ) {
			this.client = client;
			this.serverHost = serverHost;
			this.serverPort = serverPort;
			this.binaryData = binaryData;
		}

		@Override
		public void run() {
			try {
				/*
				  We have the client connection, now connect to the
				  server...
				*/
				server = new Socket( serverHost, serverPort );
				System.out.println( "Connected: " + server );
				// Grab the in and out streams of both sockets...
				InputStream cIn = client.getInputStream();
				final OutputStream cOut = client.getOutputStream();
				final InputStream sIn = server.getInputStream();
				OutputStream sOut = server.getOutputStream();
				/*
				  So that we are fully responsive to traffic from
				  either party, need 2 threads total.  We are already
				  running in one (see main), so need one more...
				*/
				Runnable r = new Runnable() {
						public void run() {
							try {
								shunt( "S", sIn, cOut );
							} catch( IOException ioe ) {
								//System.err.println( ioe );
							}
						}
					};
				Thread t = new Thread( r );
				t.start();
				/*
				  The server->client thread now in its own thread, so
				  we just do client->server, then join the thread we
				  spawned and close down the 2 sockets...
				*/
				shunt( "C", cIn, sOut );
				try {
					t.join();
				} catch( InterruptedException ie ) {
				}
				server.close();
				client.close();
				System.out.println( "Closed: " + client );
			} catch( IOException ioe ) {
				System.err.println( ioe );
			}
		}

		private void shunt( String id, InputStream is, OutputStream os )
			throws IOException {
			byte[] buf = new byte[4*1024];
			while( true ) {
				int nin  = is.read( buf );
				if( nin < 1 ) 
					break;
				process( id, buf, nin );
				os.write( buf, 0, nin );
			}
			is.close();
			os.close();
		}

		/*
		  This could be anything, currently we just write traffic to
		  stdout, converting any non-printables into '.' a la xxd if
		  the data is flagged as binary (see the -b option in main)
		*/
		void process( String id, byte[] buf, int len ) {
			System.out.println( id + " " + len );
			String s = new String( buf, 0, len );
			StringBuilder sb = new StringBuilder();
			for( int i = 0; i < s.length(); i++ ) {
				char ch = s.charAt(i);
				if( Character.isISOControl( ch ) && binaryData )
					ch = '.';
				sb.append( ch );
			}
			System.out.println( sb );
		}

		private final Socket client;
		private final String serverHost;
		private final int serverPort;
		private final boolean binaryData;
		private Socket server;
	}
}

// eof


