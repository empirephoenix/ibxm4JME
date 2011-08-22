
package ibxm;

import java.io.InputStream;

/*
	An InputStream that produces 16-bit WAV audio data from an IBXM instance.
	J2ME: java.microedition.media.Manager.createPlayer( wavInputStream, "audio/x-wav" ).start();
*/
public class WavInputStream extends InputStream {
	private static final byte[] header = {
		0x52, 0x49, 0x46, 0x46, /* "RIFF" */
		0x00, 0x00, 0x00, 0x00, /* Audio data size + 36 */
		0x57, 0x41, 0x56, 0x45, /* "WAVE" */
		0x66, 0x6D, 0x74, 0x20, /* "fmt " */
		0x10, 0x00, 0x00, 0x00, /* 16 (bytes to follow) */
		0x01, 0x00, 0x02, 0x00, /* 1 (pcm), 2 (stereo) */
		0x00, 0x00, 0x00, 0x00, /* Sample rate */
		0x00, 0x00, 0x00, 0x00, /* Sample rate * 4 */
		0x04, 0x00, 0x10, 0x00, /* 4 (bytes/frame), 16 (bits) */
		0x64, 0x61, 0x74, 0x61, /* "data" */
		0x00, 0x00, 0x00, 0x00, /* Audio data size */
	};

	private IBXM ibxm;
	private int[] mixBuf;
	private byte[] outBuf;
	private int duration, outIdx, outLen;

	public WavInputStream( IBXM ibxm ) {
		this.ibxm = ibxm;
		mixBuf = new int[ ibxm.getMixBufferLength() ];
		outBuf = new byte[ mixBuf.length * 4 ];
		duration = ibxm.calculateSongDuration();
		int samplingRate = ibxm.getSampleRate();
		System.arraycopy( header, 0, outBuf, 0, header.length );
		writeInt32( outBuf, 4, duration * 4 + 36 );
		writeInt32( outBuf, 24, samplingRate );
		writeInt32( outBuf, 28, samplingRate * 4 );
		writeInt32( outBuf, 40, duration * 4 );
		outIdx = 0;
		outLen = 44;
	}

	public int getWavFileLength() {
		return duration * 4 + header.length;
	}

	@Override
	public int read() {
		int out = outBuf[ outIdx++ ];
		if( outIdx >= outLen ) {
			getAudio();
		}
		return out;
	}

	@Override
	public int read( byte[] buf, int off, int len ) {
		int remain = outLen - outIdx;
		if( len > remain ) {
			len = remain;
		}
		System.arraycopy( outBuf, outIdx, buf, off, len );
		outIdx += len;
		if( outIdx >= outLen ) {
			getAudio();
		}
		return len;
	}

	private void getAudio() {
		int mEnd = ibxm.getAudio( mixBuf ) * 2;
		for( int mIdx = 0, oIdx = 0; mIdx < mEnd; mIdx++ ) {
			int ampl = mixBuf[ mIdx ];
			if( ampl > 32767 ) ampl = 32767;
			if( ampl < -32768 ) ampl = -32768;
			outBuf[ oIdx++ ] = ( byte ) ampl;
			outBuf[ oIdx++ ] = ( byte ) ( ampl >> 8 );
		}
		outIdx = 0;
		outLen = mEnd * 2;
	}

	private static void writeInt32( byte[] buf, int idx, int value ) {
		buf[ idx ] = ( byte ) value;
		buf[ idx + 1 ] = ( byte ) ( value >> 8 );
		buf[ idx + 2 ] = ( byte ) ( value >> 16 );
		buf[ idx + 3 ] = ( byte ) ( value >> 24 );
	}

	/* Simple Mod to Wav converter. */
	public static void main( String[] args ) throws java.io.IOException {
		// Parse arguments.
		java.io.File modFile = null, wavFile = null;
		int interpolation = Channel.LINEAR;
		if( args.length == 3 && "int=nearest".equals( args[ 0 ] ) ) {
			interpolation = Channel.NEAREST;
			modFile = new java.io.File( args[ 1 ] );
			wavFile = new java.io.File( args[ 2 ] );
		} else if( args.length == 3 && "int=sinc".equals( args[ 0 ] ) ) {
			interpolation = Channel.SINC;
			modFile = new java.io.File( args[ 1 ] );
			wavFile = new java.io.File( args[ 2 ] );
		} else if( args.length == 2 ) {
			modFile = new java.io.File( args[ 0 ] );
			wavFile = new java.io.File( args[ 1 ] );
		} else {
			System.err.println( "Mod to Wav converter for IBXM " + IBXM.VERSION );
			System.err.println( "Usage: java " + WavInputStream.class.getName() + " [int=nearest|sinc] modfile wavfile" );
			System.exit( 1 );
		}
		// Load module data into array.
		byte[] buf = new byte[ ( int ) modFile.length() ];
		InputStream in = new java.io.FileInputStream( modFile );
		int idx = 0;
		while( idx < buf.length ) {
			int len = in.read( buf, idx, buf.length - idx );
			if( len < 0 ) throw new java.io.IOException( "Unexpected end of file." );
			idx += len;
		}
		in.close();
		// Write WAV file to output.
		java.io.OutputStream out = new java.io.FileOutputStream( wavFile );
		IBXM ibxm = new IBXM( new Module( buf ), 48000 );
		ibxm.setInterpolation( interpolation );
		in = new WavInputStream( ibxm );
		buf = new byte[ ibxm.getMixBufferLength() * 4 ];
		int remain = ( ( WavInputStream ) in ).getWavFileLength();
		while( remain > 0 ) {
			int count = remain > buf.length ? buf.length : remain;
			count = in.read( buf, 0, count );
			out.write( buf, 0, count );
			remain -= count;
		}		
		out.close();
	}
}
