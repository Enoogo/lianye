These gzip fixtures contain width and height as big-endian Int values followed by ARGB pixels as big-endian Int values.

They were captured from the generated, offline `CaptureFixtureActivity` ScrollView on a Huawei Android 10 device. They contain no personal app content. Dimensions: 1080 × 2340. Section 3's background begins at y=1634 in the anchor and y=442 in the candidate, giving an independently measured forward displacement of 1192 pixels.

`ManualDeviceFrameTest` replays this content to verify that repeated paragraphs do not select an incorrect direction or seam.

`manual-chrome-anchor` and `manual-chrome-candidate` come from the same generated Activity with a 450 px fixed header, a 192 px footer, and animated rectangles between repeated sections. Section 3's background ends at y=1733 in the anchor and y=906 in the candidate: a forward displacement of 827 px. This pair reproduced a false reverse match (-993 px) when a whole-overlap average favored repeated text over an animated patch. No screenshots from Bilibili or other personal content are stored here.

`manual-chrome-gap` jumps to sections 11–13, well beyond the overlap with sections 3–5. Disabling local feature validation reproduces a false forward seam through the repeated prose; validation must reject this pair and preserve the prefix.
