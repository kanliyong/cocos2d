package org.cocos2d.nodes;

import java.nio.FloatBuffer;
import java.util.HashMap;

import javax.microedition.khronos.opengles.GL10;

import org.cocos2d.config.ccConfig;
import org.cocos2d.config.ccMacros;
import org.cocos2d.opengl.CCTexture2D;
import org.cocos2d.opengl.CCTextureAtlas;
import org.cocos2d.protocols.CCRGBAProtocol;
import org.cocos2d.protocols.CCTextureProtocol;
import org.cocos2d.types.CGAffineTransform;
import org.cocos2d.types.CGPoint;
import org.cocos2d.types.CGRect;
import org.cocos2d.types.CGSize;
import org.cocos2d.types.ccBlendFunc;
import org.cocos2d.types.ccColor3B;
import org.cocos2d.types.ccColor4B;
import org.cocos2d.types.ccQuad3;
import org.cocos2d.utils.BufferProvider;

import android.graphics.Bitmap;

/** CCSprite is a 2d image ( http://en.wikipedia.org/wiki/Sprite_(computer_graphics) )
 *
 * CCSprite can be created with an image, or with a sub-rectangle of an image.
 *
 * If the parent or any of its ancestors is a CCSpriteSheet then the following features/limitations are valid
 *	- Features when the parent is a CCSpriteSheet:
 *		- MUCH faster rendering, specially if the CCSpriteSheet has many children. All the children will be drawn in a single batch.
 *
 *	- Limitations
 *		- Camera is not supported yet (eg: CCOrbitCamera action doesn't work)
 *		- GridBase actions are not supported (eg: CCLens, CCRipple, CCTwirl)
 *		- The Alias/Antialias property belongs to CCSpriteSheet, so you can't individually set the aliased property.
 *		- The Blending function property belongs to CCSpriteSheet, so you can't individually set the blending function property.
 *		- Parallax scroller is not supported, but can be simulated with a "proxy" sprite.
 *
 *  If the parent is an standard CCNode, then CCSprite behaves like any other CCNode:
 *    - It supports blending functions
 *    - It supports aliasing / antialiasing
 *    - But the rendering will be slower: 1 draw per children.
 *
 */
public class CCSprite extends CCNode implements CCRGBAProtocol, CCTextureProtocol {

    // XXX: Optmization
    class TransformValues {
        CGPoint pos;		// position x and y
        CGPoint	scale;		// scale x and y
        float	rotation;
        CGPoint ap;			// anchor point in pixels
    }
	
	/// CCSprite invalid index on the CCSpriteSheet
	public static final int CCSpriteIndexNotInitialized = 0xffffffff;

    /** 
     * Whether or not an CCSprite will rotate, scale or translate with it's parent.
     * Useful in health bars, when you want that the health bar translates with it's parent
     * but you don't want it to rotate with its parent.
     * @since v0.99.0
     */
	//! Translate with it's parent
	public static final int CC_HONOR_PARENT_TRANSFORM_TRANSLATE =  1 << 0;
	//! Rotate with it's parent
	public static final int CC_HONOR_PARENT_TRANSFORM_ROTATE	=  1 << 1;
	//! Scale with it's parent
	public static final int CC_HONOR_PARENT_TRANSFORM_SCALE		=  1 << 2;

	//! All possible transformation enabled. Default value.
	public static final int CC_HONOR_PARENT_TRANSFORM_ALL		=  CC_HONOR_PARENT_TRANSFORM_TRANSLATE
            | CC_HONOR_PARENT_TRANSFORM_ROTATE | CC_HONOR_PARENT_TRANSFORM_SCALE;

   
	// Animations that belong to the sprite
    private HashMap<String, CCAnimation> animations_;

	// image is flipped
    /** whether or not the sprite is flipped vertically.
     * It only flips the texture of the sprite, and not the texture of the sprite's children.
     * Also, flipping the texture doesn't alter the anchorPoint.
     * If you want to flip the anchorPoint too, and/or to flip the children too use: 
     * sprite.scaleY *= -1;
    */
	public boolean flipY_;

	/** whether or not the sprite is flipped horizontally. 
     * It only flips the texture of the sprite, and not the texture of the sprite's children.
     * Also, flipping the texture doesn't alter the anchorPoint.
     * If you want to flip the anchorPoint too, and/or to flip the children too use:
     * sprite.scaleX *= -1;
    */
	public boolean flipX_;

	// opacity and RGB protocol
    /** opacity: conforms to CCRGBAProtocol protocol */
    int		opacity_;

    public int getOpacity() {
        return opacity_;
    }

    public void setOpacity(int anOpacity) {
        opacity_			= anOpacity;

        // special opacity for premultiplied textures
        if( opacityModifyRGB_ )
            setColor(colorUnmodified_);
        updateColor();
    }

    /** RGB colors: conforms to CCRGBAProtocol protocol */
	ccColor3B	color_;
	ccColor3B	colorUnmodified_;
	boolean		opacityModifyRGB_;

    public void setOpacityModifyRGB(boolean modify) {
        ccColor3B oldColor	= this.color_;
        opacityModifyRGB_	= modify;
        setColor(oldColor);
    }

    public ccColor3B getColor() {
        if(opacityModifyRGB_){
            return new ccColor3B(colorUnmodified_);
        }
        return new ccColor3B(color_);
    }

    public void setColor(ccColor3B color3) {
        color_ = new ccColor3B(color3);
        colorUnmodified_ = new ccColor3B(color3);

        if( opacityModifyRGB_ ){
            color_.r = color3.r * opacity_/255;
            color_.g = color3.g * opacity_/255;
            color_.b = color3.b * opacity_/255;
        }

        updateColor();
    }

	//
	// Data used when the sprite is self-rendered
	//
	CCTexture2D				texture_;				// Texture used to render the sprite

    /** conforms to CCTextureProtocol protocol */
	protected ccBlendFunc blendFunc_ = new ccBlendFunc(ccConfig.CC_BLEND_SRC, ccConfig.CC_BLEND_DST);
	
	// texture pixels
	CGRect rect_;
	
    /** offset position of the sprite. Calculated automatically by editors like Zwoptex.
      @since v0.99.0
    */
	CGPoint	offsetPosition_;	// absolute
	CGPoint unflippedOffsetPositionFromCenter_;

	//
	// Data used when the sprite is rendered using a CCSpriteSheet
	//
    /** weak reference of the CCTextureAtlas used when the sprite is rendered using a CCSpriteSheet */
    CCTextureAtlas			textureAtlas_;

    /** The index used on the TextureATlas.
     * Don't modify this value unless you know what you are doing */
	int                     atlasIndex_;			// Absolute (real) Index on the SpriteSheet

    /** weak reference to the CCSpriteSheet that renders the CCSprite */
	CCSpriteSheet			spriteSheet_;

	// whether or not to transform according to its parent transformations
    /** whether or not to transform according to its parent transfomrations.
     * Useful for health bars. eg: Don't rotate the health bar, even if the parent rotates.
     * IMPORTANT: Only valid if it is rendered using an CCSpriteSheet.
     * @since v0.99.0
    */
    int                     honorParentTransform_;

    /** whether or not the Sprite needs to be updated in the Atlas */
	boolean					dirty_;					// Sprite needs to be updated
	boolean					recursiveDirty_;		// Subchildren needs to be updated
	boolean					hasChildren_;			// optimization to check if it contain children
	
	// vertex coords, texture coords and color info
    /** buffers that are going to be rendered */
    /** the quad (tex coords, vertex coords and color) information */
    private FloatBuffer texCoords;
    public float[] getTexCoordsArray() {
    	float ret[] = new float[texCoords.limit()];
    	texCoords.get(ret, 0, texCoords.limit());
    	return ret;
    }
    
    private FloatBuffer vertexes;
    public float[] getVertexArray() {
    	float ret[] = new float[vertexes.limit()];
    	vertexes.get(ret, 0, vertexes.limit());
    	return ret;
    }
    
    private FloatBuffer colors;

	// whether or not it's parent is a CCSpriteSheet
    /** whether or not the Sprite is rendered using a CCSpriteSheet */
    boolean             usesSpriteSheet_;

    public CGRect getTextureRect() {
        return rect_;
    }

    /** Creates an sprite with a texture.
      The rect used will be the size of the texture.
      The offset will be (0,0).
      */
    public static CCSprite sprite(CCTexture2D texture) {
        return new CCSprite(texture);
    }

    /** Creates an sprite with a texture and a rect.
      The offset will be (0,0).
      */
    public static CCSprite sprite(CCTexture2D texture, CGRect rect) {
        return new CCSprite(texture, rect);
    }

    /** Creates an sprite with an sprite frame.
    */
    public static CCSprite sprite(CCSpriteFrame spriteFrame) {
        return new CCSprite(spriteFrame);
    }

    /** Creates an sprite with an sprite frame name.
      An CCSpriteFrame will be fetched from the CCSpriteFrameCache by name.
      If the CCSpriteFrame doesn't exist it will raise an exception.
      @since v0.9
      */
    public static CCSprite sprite(String spriteFrameName, boolean isFrame) {
        return new CCSprite(spriteFrameName, isFrame);
    }

    /** Creates an sprite with an image filename.
      The rect used will be the size of the image.
      The offset will be (0,0).
      */
    public static CCSprite sprite(String filename) {
        return new CCSprite(filename);
    }

    /** Creates an sprite with an image filename and a rect.
      The offset will be (0,0).
      */
    public static CCSprite sprite(String filename, CGRect rect) {
        return new CCSprite(filename, rect);
    }

    /** Creates an sprite with a CGImageRef.
      @deprecated Use spriteWithCGImage:key: instead. Will be removed in v1.0 final
      */
    public static CCSprite sprite(Bitmap image) {
        return new CCSprite(image);
    }

    /** Creates an sprite with a CGImageRef and a key.
      The key is used by the CCTextureCache to know if a texture was already created with this CGImage.
      For example, a valid key is: @"sprite_frame_01".
      If key is nil, then a new texture will be created each time by the CCTextureCache. 
      @since v0.99.0
      */
    public static CCSprite sprite(Bitmap image, String key) {
        return new CCSprite(image, key);
    }

    /** Creates an sprite with an CCSpriteSheet and a rect
    */
    public static CCSprite sprite(CCSpriteSheet spritesheet, CGRect rect) {
        return new CCSprite(spritesheet, rect);
    }


    /** Initializes an sprite with a texture.
      The rect used will be the size of the texture.
      The offset will be (0,0).
      */
    protected CCSprite(CCTexture2D texture) {
        CGSize size = texture.getContentSize();
        CGRect rect = CGRect.make(0, 0, size.width, size.height);
	    init(texture, rect);
    }
    
    protected CCSprite(CCTexture2D texture, CGRect rect) {
    	init(texture, rect);
    }
    
    /** Initializes an sprite with a texture and a rect.
      The offset will be (0,0).
      */
    protected void init(CCTexture2D texture, CGRect rect) {
        assert texture!=null:"Invalid texture for sprite";
        // IMPORTANT: [self init] and not [super init];
        init();
        setTexture(texture);
        setTextureRect(rect);
    }

    /** Initializes an sprite with an sprite frame.
    */
    protected CCSprite(CCSpriteFrame spriteFrame) {
    	init(spriteFrame);
    }
    
    protected void init(CCSpriteFrame spriteFrame) {
        assert spriteFrame!=null:"Invalid spriteFrame for sprite";

        init(spriteFrame.getTexture(), spriteFrame.getRect());
        setDisplayFrame(spriteFrame);    	
    }

    /** Initializes an sprite with an sprite frame name.
      An CCSpriteFrame will be fetched from the CCSpriteFrameCache by name.
      If the CCSpriteFrame doesn't exist it will raise an exception.
      @since v0.9
      */
    protected CCSprite(String spriteFrameName, boolean isFrame) {
        assert spriteFrameName!=null:"Invalid spriteFrameName for sprite";
        CCSpriteFrame frame = CCSpriteFrameCache.sharedSpriteFrameCache()
            .getSpriteFrame(spriteFrameName);
        init(frame);
    }

    /** Initializes an sprite with an image filename.
      The rect used will be the size of the image.
      The offset will be (0,0).
      */
    protected CCSprite(String filename) {
        assert filename!=null:"Invalid filename for sprite";

        CCTexture2D texture = CCTextureCache.sharedTextureCache().addImage(filename);
        if( texture != null) {
            CGRect rect = CGRect.make(0, 0, 0, 0);
            rect.size = texture.getContentSize();
            init(texture, rect);
        }
    }

    protected CCSprite() {
    	init();
    }
    
    /** Initializes an sprite with an image filename, and a rect.
      The offset will be (0,0).
      */
    protected CCSprite(String filename, CGRect rect) {
        assert filename!=null:"Invalid filename for sprite";

        CCTexture2D texture = CCTextureCache.sharedTextureCache().addImage(filename);
        if( texture != null) {
            init(texture, rect);
        }
    }

    /** Initializes an sprite with a CGImageRef
      @deprecated Use spriteWithCGImage:key: instead. Will be removed in v1.0 final
      */
    protected CCSprite(Bitmap image) {
        assert image!=null:"Invalid CGImageRef for sprite";

        // XXX: possible bug. See issue #349. New API should be added
        CCTexture2D texture = CCTextureCache.sharedTextureCache().addImage(image);

        CGSize size = texture.getContentSize();
        CGRect rect = CGRect.make(0, 0, size.width, size.height );

        init(texture, rect);
    }

    /** Initializes an sprite with a CGImageRef and a key
      The key is used by the CCTextureCache to know if a texture was already created with this CGImage.
      For example, a valid key is: @"sprite_frame_01".
      If key is nil, then a new texture will be created each time by the CCTextureCache. 
      @since v0.99.0
      */
    protected CCSprite(Bitmap image, String key) {
        assert image!=null:"Invalid CGImageRef for sprite";

        // XXX: possible bug. See issue #349. New API should be added
        CCTexture2D texture = CCTextureCache.sharedTextureCache().addImage(image);

        CGSize size = texture.getContentSize();
        CGRect rect = CGRect.make(0, 0, size.width, size.height );

        init(texture, rect);
    }

    /** Initializes an sprite with an CCSpriteSheet and a rect
    */
    protected CCSprite(CCSpriteSheet spritesheet, CGRect rect) {
        init(spritesheet.getTexture(), rect);
        useSpriteSheetRender(spritesheet);
    }

    /** updates the texture rect of the CCSprite.
    */
    public void setTextureRect(CGRect rect) {
	    setTextureRect(rect, rect.size);
    }

    /** tell the sprite to use self-render.
      @since v0.99.0
      */
    public void useSelfRender() {
        atlasIndex_ = CCSpriteIndexNotInitialized;
        usesSpriteSheet_ = false;
        textureAtlas_ = null;
        spriteSheet_ = null;
        dirty_ = recursiveDirty_ = false;

        float x1 = 0 + offsetPosition_.x;
        float y1 = 0 + offsetPosition_.y;
        float x2 = x1 + rect_.size.width;
        float y2 = y1 + rect_.size.height;

        vertexes.put(new float[]{ x1, y2, 0 });
        vertexes.put(new float[]{ x1, y1, 0 });
        vertexes.put(new float[]{ x2, y2, 0 });
        vertexes.put(new float[]{ x2, y1, 0 });
        vertexes.position(0);
    }

    /** tell the sprite to use sprite sheet render.
      @since v0.99.0
      */
    public void useSpriteSheetRender(CCSpriteSheet spriteSheet) {
        usesSpriteSheet_ = true;
        textureAtlas_ = spriteSheet.getTextureAtlas(); // weak ref
        spriteSheet_ = spriteSheet; // weak ref
    }

    protected void init() {
        texCoords = BufferProvider.createFloatBuffer(4 * 2);
        vertexes  = BufferProvider.createFloatBuffer(4 * 3);
        colors    = BufferProvider.createFloatBuffer(4 * 4);
    	
		dirty_ = false;
        recursiveDirty_ = false;
		
		// zwoptex default values
		offsetPosition_ = CGPoint.zero();
        rect_ = CGRect.make(0, 0, 1, 1);
		
		// by default use "Self Render".
		// if the sprite is added to an SpriteSheet,
        // then it will automatically switch to "SpriteSheet Render"
		useSelfRender();
		
		opacityModifyRGB_			= true;
		opacity_					= 255;
		color_                      = ccColor3B.ccWHITE;
        colorUnmodified_	        = ccColor3B.ccWHITE;
				
		// update texture (calls updateBlendFunc)
		setTexture(null);
		
		flipY_ = flipX_ = false;
		
		// lazy alloc
		animations_ = null;
		
		// default transform anchor: center
		anchorPoint_ =  CGPoint.ccp(0.5f, 0.5f);
		
		
		honorParentTransform_ = CC_HONOR_PARENT_TRANSFORM_ALL;
		hasChildren_ = false;
		
		// Atlas: Color
		float[] tmpColor = new float[]{ 1.0f, 1.0f, 1.0f, 1.0f };
		colors.put(tmpColor);
		colors.put(tmpColor);
		colors.put(tmpColor);
		colors.put(tmpColor);
		colors.position(0);
		
		// Atlas: Vertex		
		// updated in "useSelfRender"		
		// Atlas: TexCoords
		setTextureRect(CGRect.make(0, 0, 0, 0));
    }

    /** sets a new display frame to the CCSprite. */
    public void setDisplayFrame(CCSpriteFrame frame) {
        unflippedOffsetPositionFromCenter_ = frame.offset_;

        CCTexture2D newTexture = frame.getTexture();
        // update texture before updating texture rect
        if ( newTexture.name() != texture_.name())
            setTexture(newTexture);

        // update rect
        setTextureRect(frame.rect_, frame.originalSize_);
    }


    /** changes the display frame based on an animation and an index. */
    public void setDisplayFrame(String animationName, int frameIndex) {
        if (animations_ == null)
            initAnimationDictionary();

        CCAnimation anim = animations_.get(animationName);
        CCSpriteFrame frame = (CCSpriteFrame) anim.frames().get(frameIndex);
        setDisplayFrame(frame);
    }

    public void setVisible(boolean v) {
        super.setVisible(v);
        if( v != visible_ ) {
            if( usesSpriteSheet_ && ! recursiveDirty_ ) {
                dirty_ = recursiveDirty_ = true;
                for (CCNode child:children_) {
                    child.setVisible(v);
                }
            }
        }
    }


    /** adds an Animation to the Sprite. */
    public void addAnimation(CCAnimation anim) {
        // lazy alloc
        if (animations_ == null)
            initAnimationDictionary();

        animations_.put(anim.name(), anim);
    }

    /** returns an Animation given it's name. */
    public CCAnimation animationByName(String animationName) {
        assert animationName != null : "animationName parameter must be non null";
        return animations_.get(animationName);
    }

    public void updateColor() {
        float[] tmpColor = new float[]{ 
        		color_.r/255.f, color_.g/255.f, color_.b/255.f, opacity_/255.f };
		colors.put(tmpColor);
		colors.put(tmpColor);
		colors.put(tmpColor);
		colors.put(tmpColor);
		colors.position(0);
        
        // renders using Sprite Manager
        if( usesSpriteSheet_ ) {
            if( atlasIndex_ != CCSpriteIndexNotInitialized) {
                ccColor4B [] color4 = new ccColor4B[4];
                color4[0] = ccColor4B.ccc4(color_.r, color_.g, color_.b, opacity_);
                color4[1] = color4[0];
                color4[2] = color4[0];
                color4[3] = color4[0];
                
                textureAtlas_.updateColor(color4, atlasIndex_);
            } else {
                // no need to set it recursively
                // update dirty_, don't update recursiveDirty_
                dirty_ = true;
            }
        }
        // self render
        // do nothing
    }

    public void setFlipX(boolean b) {
        if( flipX_ != b ) {
            flipX_ = b;
            setTextureRect(rect_);
        }
    }

    public boolean getFlipX() {
        return flipX_;
    }

    public void setFlipY(boolean b) {
        if( flipY_ != b ) {
            flipY_ = b;	
            setTextureRect(rect_);
        }	
    }

    public boolean getFlipY() {
        return flipY_;
    }

    public void setTexture(CCTexture2D texture) {
        assert ! usesSpriteSheet_: "CCSprite: setTexture doesn't work when the sprite is rendered using a CCSpriteSheet";

        // accept texture==nil as argument
        assert (texture==null || texture instanceof CCTexture2D) 
        	: "setTexture expects a CCTexture2D. Invalid argument";
        texture_ = texture;
        updateBlendFunc();
    }

    public CCTexture2D getTexture() {
        return texture_;
    }

    /** returns whether or not a CCSpriteFrame is being displayed */
    public boolean isFrameDisplayed(CCSpriteFrame frame) {
        CGRect r = frame.getRect();
        CGPoint p = frame.getOffset();
        return (CGRect.equalToRect(r, rect_) &&
                frame.getTexture().name() == this.getTexture().name() &&
                CGPoint.equalToPoint(p, offsetPosition_));
    }

    /** returns the current displayed frame. */
    public CCSpriteFrame displayedFrame() {
	    return CCSpriteFrame.frame(getTexture(), rect_, CGPoint.zero());
    }

    // XXX HACK: optimization
    private void SET_DIRTY_RECURSIVELY() {						
        if( usesSpriteSheet_ && ! recursiveDirty_ ) {
            dirty_ = recursiveDirty_ = true;			
            if( hasChildren_)					
                setDirtyRecursively(true);
        }								
    }

    private void updateBlendFunc() {
        assert usesSpriteSheet_ :
            "CCSprite: updateBlendFunc doesn't work when the sprite is rendered using a CCSpriteSheet";

        // it's possible to have an untextured sprite
        if( texture_==null || !texture_.hasPremultipliedAlpha()) {
            blendFunc_.src = GL10.GL_SRC_ALPHA;
            blendFunc_.dst = GL10.GL_ONE_MINUS_SRC_ALPHA;
            setOpacityModifyRGB(false);
        } else {
            blendFunc_.src = ccConfig.CC_BLEND_SRC;
            blendFunc_.dst = ccConfig.CC_BLEND_DST;
            setOpacityModifyRGB(true);
        }
    }

    private void initAnimationDictionary() {
        animations_ = new HashMap<String, CCAnimation>();
    }

    private void setTextureRect(CGRect rect, CGSize size) {
        rect_ = CGRect.make(rect);

        setContentSize(size);
        updateTextureCoords(rect_);

        CGPoint relativeOffset = unflippedOffsetPositionFromCenter_;
        if (relativeOffset == null) {
        	relativeOffset = CGPoint.zero();
        }
        
        // issue #732
        if( flipX_ )
            relativeOffset.x = - relativeOffset.x;
        if( flipY_ )
            relativeOffset.y = - relativeOffset.y;

        offsetPosition_.x = relativeOffset.x + (getContentSize().width - rect_.size.width) / 2;
        offsetPosition_.y = relativeOffset.y + (getContentSize().height - rect_.size.height) / 2;

        // rendering using SpriteSheet
        if( usesSpriteSheet_ ) {
            // update dirty_, don't update recursiveDirty_
            dirty_ = true;
        } else { // self rendering
            // Atlas: Vertex
            float x1 = 0 + offsetPosition_.x;
            float y1 = 0 + offsetPosition_.y;
            float x2 = x1 + rect.size.width;
            float y2 = y1 + rect.size.height;

            // Don't update Z.
            vertexes.put(new float[]{ x1, y2, 0 });
            vertexes.put(new float[]{ x1, y1, 0 });
            vertexes.put(new float[]{ x2, y2, 0 });
            vertexes.put(new float[]{ x2, y1, 0 });
            vertexes.position(0);
        }
    }

    // XXX: Optimization: instead of calling 5 times the parent sprite to obtain: position, scale.x, scale.y, anchorpoint and rotation,
    // this fuction return the 5 values in 1 single call
    private TransformValues getTransformValues() {
        TransformValues tv = new TransformValues();
        tv.pos = position_;
        tv.scale.x = scaleX_;
        tv.scale.y = scaleY_;
        tv.rotation = rotation_;
        tv.ap = anchorPointInPixels_;

        return tv;
    }

    public boolean doesOpacityModifyRGB() {
        return opacityModifyRGB_;
    }

    public void setDirtyRecursively(boolean b) {
        dirty_ = recursiveDirty_ = b;
        // recursively set dirty
        if( hasChildren_ ) {
        	for (CCNode child: children_) {
        		CCSprite sprite = (CCSprite)child;
        		sprite.setDirtyRecursively(true);
        	}
        }
    }

    public void setPosition(CGPoint pos) {
        super.setPosition(pos);
        SET_DIRTY_RECURSIVELY();
    }

    public void setRotation(float rot) {
        super.setRotation(rot);
        SET_DIRTY_RECURSIVELY();
    }

    public void setScaleX(float sx) {
        super.setScaleX(sx);
        SET_DIRTY_RECURSIVELY();
    }

    public void setScaleY(float sy) {
        super.setScaleY(sy);
        SET_DIRTY_RECURSIVELY();
    }

    public void setScale(float s) {
        super.setScale(s);
        SET_DIRTY_RECURSIVELY();
    }

    public void setVertexZ(float z) {
        super.setVertexZ(z);
        SET_DIRTY_RECURSIVELY();
    }

    public void setAnchorPoint(CGPoint anchor) {
        super.setAnchorPoint(anchor);
        SET_DIRTY_RECURSIVELY();
    }

    public void setRelativeAnchorPoint(boolean relative) {
        assert !usesSpriteSheet_:"relativeTransformAnchor is invalid in CCSprite";
        super.setRelativeAnchorPoint(relative);
    }

    public void reorderChild(CCSprite child, int z) {
        // assert child != null: "Child must be non-nil";
        // assert children_.has(child): "Child doesn't belong to Sprite";

        if( z == child.getZOrder() )
            return;

        if( usesSpriteSheet_ ) {
            // XXX: Instead of removing/adding, it is more efficient to reorder manually
            removeChild(child, false);
            addChild(child, z);
        } else {
            super.reorderChild(child, z);
        }
    }

    public CCNode addChild(CCSprite child, int z, int aTag) {
        CCNode ret = super.addChild(child, z, aTag);

        if( usesSpriteSheet_ ) {
            int index = spriteSheet_.atlasIndex(child, z);
            spriteSheet_.insertChild(child, index);
        }

        hasChildren_ = true;

        return ret;
    }

    public void removeChild(CCSprite sprite, boolean doCleanup) {
        if( usesSpriteSheet_ )
            spriteSheet_.removeSpriteFromAtlas(sprite);

        super.removeChild(sprite, doCleanup);

        hasChildren_ = (children_.size() > 0);
    }

    public void removeAllChildren(boolean doCleanup) {
        if( usesSpriteSheet_ ) {
            for( CCNode child : children_ ) {
            	CCSprite sprite = (CCSprite)child;
            	spriteSheet_.removeSpriteFromAtlas(sprite);
            }
        }

        super.removeAllChildren(doCleanup);
        hasChildren_ = false;
    }

    public void draw(GL10 gl) {	
        assert !usesSpriteSheet_:"If CCSprite is being rendered by CCSpriteSheet, CCSprite#draw SHOULD NOT be called";

        // Default GL states: GL_TEXTURE_2D, GL_VERTEX_ARRAY, GL_COLOR_ARRAY, GL_TEXTURE_COORD_ARRAY
        // Needed states: GL_TEXTURE_2D, GL_VERTEX_ARRAY, GL_COLOR_ARRAY, GL_TEXTURE_COORD_ARRAY
        // Unneeded states: -

        boolean newBlend = false;
        if( blendFunc_.src != ccConfig.CC_BLEND_SRC || blendFunc_.dst != ccConfig.CC_BLEND_DST ) {
            newBlend = true;
            gl.glBlendFunc( blendFunc_.src, blendFunc_.dst );
        }

        // #define kQuadSize sizeof(quad_.bl)
        gl.glBindTexture(GL10.GL_TEXTURE_2D, texture_.name());

        // int offset = (int)&quad_;

        // vertex
        // int diff = offsetof( ccV3F_C4B_T2F, vertices);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0 , vertexes);

        // color
        // diff = offsetof( ccV3F_C4B_T2F, colors);
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, colors);

        // tex coords
        // diff = offsetof( ccV3F_C4B_T2F, texCoords);
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texCoords);

        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);

        if( newBlend )
            gl.glBlendFunc(ccConfig.CC_BLEND_SRC, ccConfig.CC_BLEND_DST);

        /*
        if (ccConfig.CC_SPRITE_DEBUG_DRAW) {
            CGSize s = this.contentSize();
            CGPoint vertices[]= new CGPoint [] {
                CGPoint.ccp(0,0),   CGPoint.ccp(s.width,0),
                CGPoint.ccp(s.width,s.height),  CGPoint.ccp(0,s.height)
            };
            ccDrawingPrimitives.ccDrawPoly(vertices, 4, true);
        } // CC_TEXTURENODE_DEBUG_DRAW
        */
    }

    private void updateTextureCoords(CGRect rect) {    	
        float atlasWidth = 1;
        float atlasHeight = 1;

        if (texture_ != null) {
        	 atlasWidth = texture_.pixelsWide();
        	 atlasHeight = texture_.pixelsHigh();
        }
        
        float left = rect.origin.x / atlasWidth;
        float right = (rect.origin.x + rect.size.width) / atlasWidth;
        float top = rect.origin.y / atlasHeight;
        float bottom = (rect.origin.y + rect.size.height) / atlasHeight;

        if( flipX_) {
            float tmp = left;
            left = right;
            right = tmp;
        }

        if( flipY_) {
            float tmp = top;
            top = bottom;
            bottom = tmp;
        }

        texCoords.put(0, left);
        texCoords.put(1, top);
        texCoords.put(2, left);
        texCoords.put(3, bottom);        
        texCoords.put(4, right);
        texCoords.put(5, top);
        texCoords.put(6, right);
        texCoords.put(7, bottom);
        texCoords.position(0);
    }

    /** updates the quad according the the rotation, position, scale values.
    */
    public void updateTransform() {
        CGAffineTransform matrix = CGAffineTransform.identity();

        // Optimization: if it is not visible, then do nothing
        if( ! visible_ ) {
        	ccQuad3 q = new ccQuad3();
        	textureAtlas_.putVertex(textureAtlas_.getVertexBuffer(), q.toFloatArray(), atlasIndex_);
            dirty_ = recursiveDirty_ = false;
            return ;
        }

        // Optimization: If parent is spritesheet, or parent is nil
        // build Affine transform manually
        if( parent_==null || parent_ == spriteSheet_ ) {
            float radians = -ccMacros.CC_DEGREES_TO_RADIANS(rotation_);
            float c = (float)Math.cos(radians);
            float s = (float)Math.sin(radians);

            matrix = CGAffineTransform.make( c * scaleX_,  s * scaleX_,
                    -s * scaleY_, c * scaleY_,
                    position_.x, position_.y);
            matrix = matrix.getTransformTranslate(-anchorPointInPixels_.x, -anchorPointInPixels_.y);		
        } 

        // else do affine transformation according to the HonorParentTransform
        else if( parent_ != spriteSheet_ ) {

            matrix = CGAffineTransform.identity();
            int prevHonor = CC_HONOR_PARENT_TRANSFORM_ALL;

            for (CCNode p = this; p != null && p != spriteSheet_; p = p.getParent()) {
                TransformValues tv = ((CCSprite)p).getTransformValues();
                CGAffineTransform newMatrix = CGAffineTransform.identity();
                // 2nd: Translate, Rotate, Scale
                if( (prevHonor & CC_HONOR_PARENT_TRANSFORM_TRANSLATE) !=0 )
                    newMatrix = newMatrix.getTransformTranslate(tv.pos.x, tv.pos.y);
                if( (prevHonor & CC_HONOR_PARENT_TRANSFORM_ROTATE) != 0 )
                    newMatrix = newMatrix.getTransformRotate(-ccMacros.CC_DEGREES_TO_RADIANS(tv.rotation));
                if( (prevHonor & CC_HONOR_PARENT_TRANSFORM_SCALE) != 0 ) {
                    newMatrix = newMatrix.getTransformScale(tv.scale.x, tv.scale.y);
                }

                // 3rd: Translate anchor point
                newMatrix = newMatrix.getTransformTranslate(-tv.ap.x, -tv.ap.y);
                // 4th: Matrix multiplication
                matrix =  matrix.getTransformConcat(newMatrix);
                prevHonor = ((CCSprite)p).honorParentTransform_;
            }		
        }

        //
        // calculate the Quad based on the Affine Matrix
        //	

        CGSize size = rect_.size;

        float x1 = offsetPosition_.x;
        float y1 = offsetPosition_.y;

        float x2 = x1 + size.width;
        float y2 = y1 + size.height;
        float x = (float) matrix.m02;
        float y = (float) matrix.m12;

        float cr = (float) matrix.m00;
        float sr = (float) matrix.m10;
        float cr2 = (float) matrix.m11;
        float sr2 = (float) -matrix.m01;
        float ax = x1 * cr - y1 * sr2 + x;
        float ay = x1 * sr + y1 * cr2 + y;

        float bx = x2 * cr - y1 * sr2 + x;
        float by = x2 * sr + y1 * cr2 + y;

        float cx = x2 * cr - y2 * sr2 + x;
        float cy = x2 * sr + y2 * cr2 + y;

        float dx = x1 * cr - y2 * sr2 + x;
        float dy = x1 * sr + y2 * cr2 + y;

        float v[] = new float[] { 
        	dx, dy, vertexZ_ , 	ax, ay, vertexZ_,
        	cx, cy, vertexZ_,  	bx, by, vertexZ_
        };        

        textureAtlas_.putVertex(textureAtlas_.getVertexBuffer(), v, atlasIndex_);
        dirty_ = recursiveDirty_ = false;
    }

	@Override
	public ccBlendFunc getBlendFunc() {
		return blendFunc_;
	}

	@Override
	public void setBlendFunc(ccBlendFunc blendFunc) {
		blendFunc_ = blendFunc;
	}

}
