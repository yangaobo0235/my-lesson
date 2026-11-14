"""${message}"""

from alembic import op
import sqlalchemy as sa
${imports if imports else ""}

revision = ${repr(up_revision)}
down_revision = ${repr(down_revision)}
branch_labels = ${repr(branch_labels)}
depends_on = ${repr(depends_on)}


def upgrade() -> None:
    """执行本版本需要的结构变更。"""
    ${upgrades if upgrades else "pass"}


def downgrade() -> None:
    """按本版本明确的数据保留条件回退结构。"""
    ${downgrades if downgrades else 'raise RuntimeError("本版本未提供自动回退，请按恢复方案处理")'}
