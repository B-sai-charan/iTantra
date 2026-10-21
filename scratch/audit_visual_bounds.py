import pptx
from pptx.util import Inches, Pt

def audit_layout():
    prs = pptx.Presentation("scratch/iTantra-SIH-2026-Final-Presentation.pptx")
    print(f"Presentation Dimensions: {prs.slide_width.inches} x {prs.slide_height.inches} inches")
    
    for idx, slide in enumerate(prs.slides, start=1):
        print(f"\n==================== SLIDE {idx} ====================")
        notes = slide.notes_slide.notes_text_frame.text if slide.has_notes_slide else "NO NOTES"
        print(f"Notes Length: {len(notes)} chars")
        print(f"Shapes Count: {len(slide.shapes)}")
        
        for s_idx, shape in enumerate(slide.shapes):
            left = shape.left.inches if shape.left else 0
            top = shape.top.inches if shape.top else 0
            width = shape.width.inches if shape.width else 0
            height = shape.height.inches if shape.height else 0
            
            # Check bounds within 13.333 x 7.5
            out_of_bounds = (left < 0 or top < 0 or (left + width) > 13.34 or (top + height) > 7.51)
            
            if shape.has_text_frame:
                text_sample = shape.text_frame.text.replace("\n", " ")[:60]
                print(f"  Shape {s_idx} [TEXT]: L={left:.2f}, T={top:.2f}, W={width:.2f}, H={height:.2f} | OutOfBounds: {out_of_bounds} | Text: {text_sample}...")
            elif shape.has_table:
                print(f"  Shape {s_idx} [TABLE]: L={left:.2f}, T={top:.2f}, W={width:.2f}, H={height:.2f} | OutOfBounds: {out_of_bounds} | Rows: {len(shape.table.rows)}, Cols: {len(shape.table.columns)}")
            else:
                print(f"  Shape {s_idx} [SHAPE]: L={left:.2f}, T={top:.2f}, W={width:.2f}, H={height:.2f} | OutOfBounds: {out_of_bounds}")

if __name__ == "__main__":
    audit_layout()
