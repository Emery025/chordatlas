package org.twak.viewTrace.facades;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.Tweed;
import org.twak.utils.Imagez;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.franken.App;

/**
 * Utilties to synthesize facade using Pix2Pix network
 */

public class Pix2Pix {

	String netName;
	int resolution;
	
	public Pix2Pix (App exemplar) {
		this.netName = exemplar.netName;
		this.resolution = exemplar.resolution;
	}
	
	public interface JobResult {
		public void finished ( Map<Object, File>  results);
	}
	
	public static class Job {
		JobResult finished;
		public String name;
		boolean encode= false;
		
		public Job (JobResult finished) {
			this.finished = finished;
			this.name = System.nanoTime() +":"+ Math.random();
		}
		
		public Job (JobResult finished, boolean encode) {
			this (finished);
			this.encode = encode;
		}
	}
	
	public void submit( Job job ) {
//		synchronized (job.network.intern()) {
			submitSafe(job);
//		}
	}
	
	public void submitSafe( Job job ) {
		
		String network = netName;
		if (job.encode)
			network = network+"_e";
			
		
		File go     = new File( "/home/twak/code/bikegan/input/"  + netName + "/val/go" );
		File outDir = new File( "/home/twak/code/bikegan/output/" + netName +"/" + job.name );
		
		try {
			FileWriter  fos = new FileWriter( go );
			fos.write( job.name );
			fos.close();
			
			
		} catch ( Throwable e1 ) {
			e1.printStackTrace();
		}

		long startTime = System.currentTimeMillis();

		do {
			try {
				Thread.sleep( 50 );
			} catch ( InterruptedException e ) {
				e.printStackTrace();
			}
		} while ( go.exists() && System.currentTimeMillis() - startTime < 1e5 );

		startTime = System.currentTimeMillis();

		if (go.exists()) {
			System.out.println( "failed to get a result "+ go.getAbsolutePath() );
			return;
		}
			
		do {

			try {
				Thread.sleep( 50 );
			} catch ( InterruptedException e ) {
				e.printStackTrace();
			}

			if ( outDir.exists() ) {
				
				System.out.println( "processing "+job.name );
				
				Map<Object, File> done =new HashMap<>();
				
				for (Map.Entry<Object, String> e : inputs.entrySet())
					done.put( e.getKey(), new File (outDir, e.getValue()+".png") );
				
				job.finished.finished( done );
				
				try {
					FileUtils.deleteDirectory( outDir );
				} catch ( IOException e ) {
					e.printStackTrace();
				}
				return;
			}
			
		} while ( System.currentTimeMillis() - startTime < 3000 );
		
		System.out.println( "timeout trying to get result "+ job.name );
		
	}
	
	public void encode(File f, double[] values, Runnable update ) {

		try {
			
			BufferedImage bi = ImageIO.read( f );
			bi = Imagez.scaleSquare( bi, resolution );
			bi = Imagez.join( bi, bi );

			File dir = new File( "/home/twak/code/bikegan/input/" + netName + "_e/val/" );
			dir.mkdirs();
			ImageIO.write( bi, "png", new File( dir, System.nanoTime() + ".png" ) );

			submit( new Job( new JobResult() {

				@Override
				public void finished( Map<Object, File> results ) {
					for ( File zf : results.values() ) {
						String[] ss = zf.getName().split( "_" );
						for ( int i = 0; i < ss.length; i++ )
							values[ i ] = Double.parseDouble( ss[ i ] );
						update.run();
						return;
						
					}
				}
			}, true ) );

		} catch ( Throwable e ) {
			e.printStackTrace();
		}
		
	}

	public static String importTexture( File texture, int specular, Map<Color, Color> specLookup, DRectangle crop ) throws IOException {
		
//		String name = f.get

		new File( Tweed.SCRATCH ).mkdirs();

//		File texture = new File( f, name + ".png" );
		String dest = "missing";
		if ( texture.exists() && texture.length() > 0 ) {

			BufferedImage rgb = ImageIO.read( texture );
			
			BufferedImage labels = ImageIO.read( new File( texture.getParentFile(), texture.getName() + "_label" ) ); 

			if (crop != null) {
				
				rgb = scaleToFill ( rgb, crop );//   .getSubimage( (int) crop.x, (int) crop.y, (int) crop.width, (int) crop.height );
				labels = scaleToFill ( labels, crop );
			}
			
			NormSpecGen ns = new NormSpecGen( rgb, labels, specLookup );
			
			if (specular >= 0) {
				Graphics2D g = ns.spec.createGraphics();
				g.setColor( new Color (specular, specular, specular) );
				g.fillRect( 0, 0, ns.spec.getWidth(), ns.spec.getHeight() );
				g.dispose();
			}

			dest =  "scratch/" + UUID.randomUUID();
			ImageIO.write( rgb    , "png", new File( Tweed.DATA + "/" + ( dest + ".png" ) ) );
			ImageIO.write( ns.norm, "png", new File( Tweed.DATA + "/" + ( dest + "_norm.png" ) ) );
			ImageIO.write( ns.spec, "png", new File( Tweed.DATA + "/" + ( dest + "_spec.png" ) ) );
			ImageIO.write ( labels, "png", new File( Tweed.DATA + "/" + ( dest + "_lab.png" ) ) );
			texture.delete();
		}
		return dest + ".png";
	}
	
	public static BufferedImage scaleToFill( BufferedImage rgb, DRectangle crop ) {
		
		BufferedImage out = new BufferedImage (rgb.getWidth(), rgb.getHeight(), rgb.getType() );
		
		Graphics2D g = out.createGraphics();
		g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR );
		g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
		
		int cropy = (int) (256 - crop.height - crop.y);
		g.drawImage( rgb, 0, 0, rgb.getWidth(), rgb.getHeight(), 
				(int) crop.x, cropy, (int) (crop.x + crop.width), (int) (cropy + crop.height ),
				null );
		g.dispose();
		
		return out;
	}

	public static DRectangle findBounds( MiniFacade toEdit ) {
		
		if ( toEdit.postState == null ) 
			return toEdit.getAsRect();
		else 
			return toEdit.postState.outerFacadeRect;
	}

	Map<Object, String> inputs = new HashMap<>();
	
	public void addInput( BufferedImage bi, Object key, double[] styleZ ) {
		try {

			String name = UUID.randomUUID() +"";
			
			File dir = new File( "/home/twak/code/bikegan/input/" + netName + "/val/" );
			dir.mkdirs();
			String nameWithZ = name + zAsString( styleZ );
			inputs.put( key, nameWithZ );
			
			ImageIO.write( bi, "png", new File( dir, nameWithZ + ".png" ) );
			
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}
	
	public static String zAsString(double[] z) {
		String zs = "";
		for ( double d : z )
			zs += "_" + d;
		return zs;
	}
}
