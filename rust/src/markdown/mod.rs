#[derive(uniffi::Enum, Clone, Copy, PartialEq, Eq, Debug)]
pub enum SpanStyle {
    Plain,
    Bold,
    Italic,
    Code,
}

#[derive(uniffi::Record, Clone, PartialEq, Eq, Debug)]
pub struct Span {
    pub text: String,
    pub style: SpanStyle,
}

#[derive(uniffi::Enum, Clone, Debug)]
pub enum MdBlock {
    Heading {
        level: u8,
        spans: Vec<Span>,
    },
    Paragraph {
        spans: Vec<Span>,
    },
    Bullet {
        spans: Vec<Span>,
    },
    Numbered {
        number: String,
        spans: Vec<Span>,
    },
    Code {
        code: String,
    },
    Table {
        header: Vec<Vec<Span>>,
        rows: Vec<Vec<Vec<Span>>>,
    },
    Spacer,
}

mod block;
mod inline;

#[cfg(test)]
mod tests;
